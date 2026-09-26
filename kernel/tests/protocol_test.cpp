#include "protocol.hpp"

#include <stdexcept>
#include <string>

int main() {
    using namespace madre::kernel;
    Frame original{MessageType::Submit, 42, {{"a", "b"}, {"candidate.0.kind", "PROCESS"}}, {1, 2, 3}};
    const auto encoded = encode_frame(original);
    if (encoded.empty()) throw std::runtime_error("encoded frame is empty");
    bool rejected = false;
    try {
        Frame invalid{MessageType::Submit, 1, {{"bad", "line\nvalue"}}, {}};
        (void)encode_frame(invalid);
    } catch (const FramingError&) {
        rejected = true;
    }
    if (!rejected) throw std::runtime_error("invalid metadata was accepted");
    return 0;
}
