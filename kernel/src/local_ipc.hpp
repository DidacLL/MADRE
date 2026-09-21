#pragma once

#include <chrono>
#include <filesystem>
#include <memory>

namespace madre::kernel {

class LocalIpcServer {
public:
    LocalIpcServer(std::filesystem::path endpoint, std::filesystem::path data_dir);
    ~LocalIpcServer();

    LocalIpcServer(const LocalIpcServer&) = delete;
    LocalIpcServer& operator=(const LocalIpcServer&) = delete;

    int accept_for(std::chrono::milliseconds timeout);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

void close_local_ipc(int fd) noexcept;

}  // namespace madre::kernel
