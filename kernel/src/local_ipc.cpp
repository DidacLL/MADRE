#include "local_ipc.hpp"

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <string>

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#include <windows.h>
#else
#include <fcntl.h>
#include <poll.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>
#endif

namespace fs = std::filesystem;

namespace madre::kernel {

struct LocalIpcServer::Impl {
    fs::path endpoint;
    fs::path lock_path;
#ifdef _WIN32
    HANDLE lock_handle{INVALID_HANDLE_VALUE};
#else
    int lock_fd{-1};
    int server_fd{-1};
    bool endpoint_owned{};
#endif

    Impl(fs::path endpoint_value, fs::path data_dir)
        : endpoint(std::move(endpoint_value)) {
#ifndef _WIN32
        (void)data_dir;
#endif
#ifdef _WIN32
        if (endpoint.wstring().rfind(L"\\\\.\\pipe\\", 0) != 0) {
            throw std::runtime_error(
                "Windows Kernel endpoint must be a local named pipe");
        }
        fs::create_directories(data_dir);
        lock_path = data_dir / "kernel.endpoint.lock";
        lock_handle = ::CreateFileW(
            lock_path.c_str(), GENERIC_READ | GENERIC_WRITE, 0, nullptr,
            OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (lock_handle == INVALID_HANDLE_VALUE) {
            throw std::runtime_error(
                "acquire Kernel endpoint lock failed: Windows error " +
                std::to_string(::GetLastError()));
        }
#else
        lock_path = endpoint;
        lock_path += ".lock";
        try {
            if (!lock_path.parent_path().empty()) {
                fs::create_directories(lock_path.parent_path());
            }
            lock_fd = ::open(
                lock_path.c_str(), O_CREAT | O_RDWR | O_CLOEXEC,
                S_IRUSR | S_IWUSR);
            if (lock_fd < 0) {
                throw std::runtime_error(
                    "open Kernel endpoint lock failed: " +
                    std::string(std::strerror(errno)));
            }
            if (::flock(lock_fd, LOCK_EX | LOCK_NB) != 0) {
                const int lock_error = errno;
                if (lock_error == EWOULDBLOCK || lock_error == EAGAIN) {
                    throw std::runtime_error(
                        "Kernel endpoint lock is already held");
                }
                throw std::runtime_error(
                    "acquire Kernel endpoint lock failed: " +
                    std::string(std::strerror(lock_error)));
            }
            if (endpoint.string().size() >= sizeof(sockaddr_un::sun_path)) {
                throw std::runtime_error("Unix-domain socket path is too long");
            }
            if (!endpoint.parent_path().empty()) {
                fs::create_directories(endpoint.parent_path());
            }
            std::error_code error;
            fs::remove(endpoint, error);
            server_fd = ::socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
            if (server_fd < 0) {
                throw std::runtime_error(
                    "create Unix-domain socket failed: " +
                    std::string(std::strerror(errno)));
            }
            sockaddr_un address{};
            address.sun_family = AF_UNIX;
            std::snprintf(
                address.sun_path, sizeof(address.sun_path), "%s",
                endpoint.c_str());
            if (::bind(
                    server_fd, reinterpret_cast<sockaddr*>(&address),
                    sizeof(address)) != 0) {
                throw std::runtime_error(
                    "bind Unix-domain socket failed: " +
                    std::string(std::strerror(errno)));
            }
            endpoint_owned = true;
            if (::chmod(endpoint.c_str(), S_IRUSR | S_IWUSR) != 0) {
                throw std::runtime_error(
                    "set owner-only Unix-domain socket permissions failed: " +
                    std::string(std::strerror(errno)));
            }
            if (::listen(server_fd, 16) != 0) {
                throw std::runtime_error(
                    "listen on Unix-domain socket failed: " +
                    std::string(std::strerror(errno)));
            }
        } catch (...) {
            cleanup();
            throw;
        }
#endif
    }

    ~Impl() { cleanup(); }

    void cleanup() noexcept {
#ifdef _WIN32
        if (lock_handle != INVALID_HANDLE_VALUE) {
            ::CloseHandle(lock_handle);
            lock_handle = INVALID_HANDLE_VALUE;
        }
        if (!lock_path.empty()) {
            std::error_code error;
            fs::remove(lock_path, error);
        }
#else
        if (server_fd >= 0) {
            ::close(server_fd);
            server_fd = -1;
        }
        if (endpoint_owned && !endpoint.empty()) {
            std::error_code error;
            fs::remove(endpoint, error);
            endpoint_owned = false;
        }
        if (lock_fd >= 0) {
            ::close(lock_fd);
            lock_fd = -1;
        }
#endif
    }

    int accept_for(std::chrono::milliseconds timeout) {
#ifdef _WIN32
        HANDLE pipe = ::CreateNamedPipeW(
            endpoint.c_str(),
            PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
            PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT |
                PIPE_REJECT_REMOTE_CLIENTS,
            PIPE_UNLIMITED_INSTANCES, 2U * 1024U * 1024U,
            2U * 1024U * 1024U, 0, nullptr);
        if (pipe == INVALID_HANDLE_VALUE) {
            throw std::runtime_error(
                "create Windows named pipe failed: Windows error " +
                std::to_string(::GetLastError()));
        }
        HANDLE event = ::CreateEventW(nullptr, TRUE, FALSE, nullptr);
        if (event == nullptr) {
            const auto error = ::GetLastError();
            ::CloseHandle(pipe);
            throw std::runtime_error(
                "create Windows named-pipe event failed: Windows error " +
                std::to_string(error));
        }
        OVERLAPPED overlapped{};
        overlapped.hEvent = event;
        bool connected = false;
        const BOOL result = ::ConnectNamedPipe(pipe, &overlapped);
        if (result) {
            connected = true;
        } else {
            const auto error = ::GetLastError();
            if (error == ERROR_PIPE_CONNECTED) {
                connected = true;
            } else if (error == ERROR_IO_PENDING) {
                const auto wait_result = ::WaitForSingleObject(
                    event, static_cast<DWORD>(timeout.count()));
                if (wait_result == WAIT_OBJECT_0) {
                    DWORD transferred = 0;
                    if (::GetOverlappedResult(
                            pipe, &overlapped, &transferred, FALSE) ||
                        ::GetLastError() == ERROR_PIPE_CONNECTED) {
                        connected = true;
                    } else {
                        const auto final_error = ::GetLastError();
                        ::CloseHandle(event);
                        ::CloseHandle(pipe);
                        throw std::runtime_error(
                            "connect Windows named pipe failed: Windows error " +
                            std::to_string(final_error));
                    }
                } else if (wait_result == WAIT_TIMEOUT) {
                    ::CancelIoEx(pipe, &overlapped);
                    ::WaitForSingleObject(event, INFINITE);
                    ::CloseHandle(event);
                    ::CloseHandle(pipe);
                    return -1;
                } else {
                    const auto wait_error = ::GetLastError();
                    ::CancelIoEx(pipe, &overlapped);
                    ::WaitForSingleObject(event, INFINITE);
                    ::CloseHandle(event);
                    ::CloseHandle(pipe);
                    throw std::runtime_error(
                        "wait for Windows named-pipe client failed: Windows error " +
                        std::to_string(wait_error));
                }
            } else {
                ::CloseHandle(event);
                ::CloseHandle(pipe);
                throw std::runtime_error(
                    "connect Windows named pipe failed: Windows error " +
                    std::to_string(error));
            }
        }
        ::CloseHandle(event);
        if (!connected) {
            ::CloseHandle(pipe);
            return -1;
        }
        const int fd = ::_open_osfhandle(
            reinterpret_cast<std::intptr_t>(pipe), _O_BINARY);
        if (fd < 0) {
            ::CloseHandle(pipe);
            throw std::runtime_error(
                "convert Windows named pipe to framed IPC descriptor failed");
        }
        return fd;
#else
        pollfd ready{server_fd, POLLIN, 0};
        const int poll_result = ::poll(
            &ready, 1, static_cast<int>(timeout.count()));
        if (poll_result < 0) {
            if (errno == EINTR) return -1;
            throw std::runtime_error(
                "poll local IPC socket failed: " +
                std::string(std::strerror(errno)));
        }
        if (poll_result == 0) return -1;
        const int fd = ::accept4(
            server_fd, nullptr, nullptr, SOCK_CLOEXEC);
        if (fd < 0) {
            if (errno == EINTR) return -1;
            throw std::runtime_error(
                "accept local IPC client failed: " +
                std::string(std::strerror(errno)));
        }
        return fd;
#endif
    }
};

LocalIpcServer::LocalIpcServer(fs::path endpoint, fs::path data_dir)
    : impl_(std::make_unique<Impl>(
          std::move(endpoint), std::move(data_dir))) {}
LocalIpcServer::~LocalIpcServer() = default;

int LocalIpcServer::accept_for(std::chrono::milliseconds timeout) {
    return impl_->accept_for(timeout);
}

void close_local_ipc(int fd) noexcept {
    if (fd < 0) return;
#ifdef _WIN32
    ::_close(fd);
#else
    ::close(fd);
#endif
}

}  // namespace madre::kernel
