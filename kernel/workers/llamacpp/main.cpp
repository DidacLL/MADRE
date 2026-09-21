#include "protocol.hpp"

#include "llama.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unistd.h>
#include <vector>

namespace madre::kernel {
namespace {

struct Options {
    std::string engine_id;
    std::string model_path;
    int n_predict{64};
    std::uint32_t context_size{512};
    int gpu_layers{};
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument = argv[i];
        if (argument == "--engine-id" && i + 1 < argc) {
            options.engine_id = argv[++i];
        } else if (argument == "--model" && i + 1 < argc) {
            options.model_path = argv[++i];
        } else if (argument == "--n-predict" && i + 1 < argc) {
            options.n_predict = std::stoi(argv[++i]);
        } else if (argument == "--ctx-size" && i + 1 < argc) {
            options.context_size =
                static_cast<std::uint32_t>(std::stoul(argv[++i]));
        } else if (argument == "--gpu-layers" && i + 1 < argc) {
            options.gpu_layers = std::stoi(argv[++i]);
        } else {
            throw std::runtime_error(
                "usage: madre-llamacpp-worker --engine-id <id> --model <gguf> "
                "[--n-predict N] [--ctx-size N] [--gpu-layers N]");
        }
    }
    if (options.engine_id.empty() || options.model_path.empty() ||
        options.n_predict < 1 || options.context_size < 32 ||
        options.gpu_layers < 0) {
        throw std::runtime_error("invalid llama.cpp worker configuration");
    }
    return options;
}

void send_failure(const Frame& request, const std::string& message) {
    write_frame(
        STDOUT_FILENO,
        Frame{
            MessageType::WorkerFailure,
            request.correlation_id,
            {{"technical_failure", "LLAMACPP_WORKER_FAILURE: " + message}},
            {},
        });
}

std::string token_piece(const llama_vocab* vocab, llama_token token) {
    std::array<char, 256> fixed{};
    int size =
        llama_token_to_piece(vocab, token, fixed.data(), fixed.size(), 0, true);
    if (size >= 0) {
        return std::string(fixed.data(), static_cast<std::size_t>(size));
    }
    std::vector<char> dynamic(static_cast<std::size_t>(-size));
    size = llama_token_to_piece(
        vocab, token, dynamic.data(), dynamic.size(), 0, true);
    if (size < 0) {
        throw std::runtime_error(
            "failed to convert generated token to bytes");
    }
    return std::string(dynamic.data(), static_cast<std::size_t>(size));
}

std::vector<llama_token> tokenize(
    const llama_vocab* vocab, const std::string& prompt) {
    const int required = -llama_tokenize(
        vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (required <= 0) {
        throw std::runtime_error("failed to size prompt tokenization");
    }
    std::vector<llama_token> tokens(static_cast<std::size_t>(required));
    const int actual = llama_tokenize(
        vocab,
        prompt.data(),
        prompt.size(),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true);
    if (actual < 0) {
        throw std::runtime_error("failed to tokenize prompt");
    }
    tokens.resize(static_cast<std::size_t>(actual));
    return tokens;
}

std::vector<std::uint8_t> generate(
    const Options& options,
    llama_model* model,
    const llama_vocab* vocab,
    const std::vector<std::uint8_t>& payload) {
    const std::string prompt(payload.begin(), payload.end());
    auto prompt_tokens = tokenize(vocab, prompt);
    if (prompt_tokens.size() +
            static_cast<std::size_t>(options.n_predict) + 1 >
        options.context_size) {
        throw std::runtime_error(
            "prompt plus configured generation exceeds worker context size");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = options.context_size;
    context_params.n_batch = std::min<std::uint32_t>(
        options.context_size,
        std::max<std::uint32_t>(
            32, static_cast<std::uint32_t>(prompt_tokens.size())));
    context_params.no_perf = true;

    llama_context* context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        throw std::runtime_error("failed to create llama context");
    }

    llama_sampler_chain_params sampler_params =
        llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler* sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) {
        llama_free(context);
        throw std::runtime_error("failed to create llama sampler");
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    try {
        llama_batch batch = llama_batch_get_one(
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()));
        if (llama_decode(context, batch) != 0) {
            throw std::runtime_error("failed to evaluate prompt");
        }

        std::vector<std::uint8_t> result;
        for (int generated = 0; generated < options.n_predict; ++generated) {
            const llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            const auto piece = token_piece(vocab, token);
            if (result.size() + piece.size() >
                kMaxC1TextGenerationOpaquePayloadBytes) {
                throw std::runtime_error(
                    "generated result exceeds bounded C1 payload");
            }
            result.insert(result.end(), piece.begin(), piece.end());

            llama_token next = token;
            batch = llama_batch_get_one(&next, 1);
            if (llama_decode(context, batch) != 0) {
                throw std::runtime_error(
                    "failed to evaluate generated token");
            }
        }

        llama_sampler_free(sampler);
        llama_free(context);
        return result;
    } catch (...) {
        llama_sampler_free(sampler);
        llama_free(context);
        throw;
    }
}

void execute(
    const Options& options,
    llama_model* model,
    const llama_vocab* vocab,
    const Frame& request) {
    if (request.type != MessageType::WorkerExecute) {
        send_failure(request, "unsupported worker request");
        return;
    }
    if (metadata_value(request, "engine_id") != options.engine_id) {
        send_failure(request, "engine identity mismatch");
        return;
    }
    try {
        auto result = generate(options, model, vocab, request.payload);
        write_frame(
            STDOUT_FILENO,
            Frame{
                MessageType::WorkerResult,
                request.correlation_id,
                {},
                std::move(result),
            });
    } catch (const std::exception& ex) {
        send_failure(request, ex.what());
    }
}

}  // namespace
}  // namespace madre::kernel

int main(int argc, char** argv) {
    try {
        const auto options = madre::kernel::parse_options(argc, argv);
        ggml_backend_load_all();

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = options.gpu_layers;
        llama_model* model =
            llama_model_load_from_file(options.model_path.c_str(), model_params);
        if (model == nullptr) {
            throw std::runtime_error("failed to load configured GGUF model");
        }
        const llama_vocab* vocab = llama_model_get_vocab(model);
        if (vocab == nullptr) {
            llama_model_free(model);
            throw std::runtime_error("loaded model has no vocabulary");
        }

        while (true) {
            madre::kernel::Frame request;
            try {
                request = madre::kernel::read_frame(STDIN_FILENO);
            } catch (const std::runtime_error& ex) {
                if (std::string_view(ex.what()) ==
                    "peer closed framed IPC") {
                    llama_model_free(model);
                    return 0;
                }
                llama_model_free(model);
                throw;
            }
            madre::kernel::execute(options, model, vocab, request);
        }
    } catch (const std::exception& ex) {
        std::cerr << "madre-llamacpp-worker: " << ex.what() << '\n';
        return 1;
    }
}
