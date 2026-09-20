#pragma once

#include "protocol.hpp"

#include <cstdint>
#include <filesystem>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace madre::kernel {

struct ResourceRequirement {
    int cpu_slots{1};
    std::uint64_t ram_bytes{};
    std::string gpu_id;
    std::uint64_t gpu_vram_bytes{};
};

struct ResourceCapacity {
    int cpu_slots{1};
    std::uint64_t ram_bytes{};
    std::map<std::string, std::uint64_t> gpu_vram_bytes;
};

class ResourceManager {
public:
    class Lease {
    public:
        Lease() = default;
        Lease(const Lease&) = delete;
        Lease& operator=(const Lease&) = delete;
        Lease(Lease&& other) noexcept;
        Lease& operator=(Lease&& other) noexcept;
        ~Lease();

    private:
        friend class ResourceManager;
        Lease(ResourceManager* owner, ResourceRequirement requirement);
        void reset() noexcept;

        ResourceManager* owner_{};
        ResourceRequirement requirement_;
    };

    explicit ResourceManager(ResourceCapacity capacity);
    bool can_ever_reserve(const ResourceRequirement& requirement);
    std::optional<Lease> try_reserve(const ResourceRequirement& requirement);

private:
    friend class Lease;
    void release(const ResourceRequirement& requirement) noexcept;

    ResourceCapacity capacity_;
    int cpu_used_{};
    std::uint64_t ram_used_{};
    std::map<std::string, std::uint64_t> gpu_vram_used_;
    std::mutex mutex_;
};

enum class WorkerOutcomeKind {
    Succeeded,
    TechnicalFailure,
    Crashed,
    Cancelled,
    TimedOut,
    Stopped,
};

struct WorkerOutcome {
    WorkerOutcomeKind kind{WorkerOutcomeKind::TechnicalFailure};
    std::vector<std::uint8_t> payload;
    std::string technical_failure;
};

class WorkerPool {
private:
    struct WorkerProcess;

public:
    class Lease {
    public:
        Lease() = default;
        Lease(const Lease&) = delete;
        Lease& operator=(const Lease&) = delete;
        Lease(Lease&& other) noexcept;
        Lease& operator=(Lease&& other) noexcept;
        ~Lease();

        WorkerOutcome execute(
            const std::vector<std::uint8_t>& payload,
            std::uint64_t correlation_id,
            std::int64_t attempt_started_at_ms,
            const std::function<bool()>& cancellation_requested,
            const std::function<bool()>& kernel_stopping,
            std::optional<std::int64_t> stop_at_ms,
            bool timeout_wins);

        int pid() const;

    private:
        friend class WorkerPool;
        Lease(WorkerPool* pool, std::shared_ptr<WorkerProcess> worker);
        void reset() noexcept;

        WorkerPool* pool_{};
        std::shared_ptr<WorkerProcess> worker_;
    };

    WorkerPool(std::filesystem::path executable, int fake_delay_ms, std::int64_t idle_timeout_ms);
    ~WorkerPool();

    WorkerPool(const WorkerPool&) = delete;
    WorkerPool& operator=(const WorkerPool&) = delete;

    Lease acquire(const std::string& engine_id);
    bool is_warm(std::string_view engine_id);
    void reap_idle(std::int64_t now_ms);
    void shutdown() noexcept;

private:
    void release(const std::shared_ptr<WorkerProcess>& worker) noexcept;

    std::filesystem::path executable_;
    int fake_delay_ms_{};
    std::int64_t idle_timeout_ms_{};
    std::vector<std::shared_ptr<WorkerProcess>> workers_;
    std::mutex mutex_;
};

}  // namespace madre::kernel
