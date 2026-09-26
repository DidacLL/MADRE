#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>

namespace fs = std::filesystem;

int main(int argc, char** argv) {
    try {
        std::string mode = "echo";
        int delay_ms = 0;
        fs::path marker;
        for (int i = 1; i < argc; ++i) {
            const std::string arg = argv[i];
            if (arg == "--mode" && i + 1 < argc) mode = argv[++i];
            else if (arg == "--delay-ms" && i + 1 < argc) delay_ms = std::stoi(argv[++i]);
            else if (arg == "--marker" && i + 1 < argc) marker = argv[++i];
            else throw std::runtime_error("invalid process fixture argument");
        }
        std::string input((std::istreambuf_iterator<char>(std::cin)), std::istreambuf_iterator<char>());
        if (!marker.empty()) {
            std::ofstream out(marker, std::ios::app);
            out << "attempt\n";
            out.flush();
        }
        if (delay_ms > 0) std::this_thread::sleep_for(std::chrono::milliseconds(delay_ms));
        if (mode == "fail") {
            std::cerr << "deterministic fixture failure";
            return 17;
        }
        if (mode != "echo") throw std::runtime_error("unknown fixture mode");
        std::cout << input;
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << ex.what();
        return 2;
    }
}
