cmake_minimum_required(VERSION 3.20)
project(madre_kernel LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SQLite3 REQUIRED)
find_package(Threads REQUIRED)

function(madre_target_warnings target)
    if(MSVC)
        target_compile_definitions(${target} PRIVATE NOMINMAX _CRT_SECURE_NO_WARNINGS)
        target_compile_options(${target} PRIVATE /W4 /WX /permissive-)
    else()
        target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
endfunction()

add_library(madre_kernel_protocol STATIC src/protocol.cpp)
target_include_directories(madre_kernel_protocol PUBLIC src)
madre_target_warnings(madre_kernel_protocol)

add_executable(madre-kernel src/main.cpp src/store.cpp src/worker_runtime.cpp src/local_ipc.cpp)
target_include_directories(madre-kernel PRIVATE src)
target_link_libraries(madre-kernel PRIVATE madre_kernel_protocol SQLite::SQLite3 Threads::Threads)
madre_target_warnings(madre-kernel)

add_executable(madre-fake-worker tests/fake_worker.cpp)
target_link_libraries(madre-fake-worker PRIVATE madre_kernel_protocol Threads::Threads)
madre_target_warnings(madre-fake-worker)

enable_testing()
add_executable(protocol-test tests/protocol_test.cpp)
target_link_libraries(protocol-test PRIVATE madre_kernel_protocol Threads::Threads)
madre_target_warnings(protocol-test)
add_test(NAME protocol-round-trip COMMAND protocol-test)


option(MADRE_BUILD_LLAMACPP_WORKER "Build the optional native llama.cpp worker" OFF)
set(MADRE_LLAMA_CPP_DIR "" CACHE PATH "Path to a llama.cpp source checkout")

if(MADRE_BUILD_LLAMACPP_WORKER)
    if(NOT MADRE_LLAMA_CPP_DIR OR NOT EXISTS "${MADRE_LLAMA_CPP_DIR}/CMakeLists.txt")
        message(FATAL_ERROR "MADRE_BUILD_LLAMACPP_WORKER requires MADRE_LLAMA_CPP_DIR")
    endif()

    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    add_subdirectory(
        "${MADRE_LLAMA_CPP_DIR}"
        "${CMAKE_BINARY_DIR}/llama.cpp"
        EXCLUDE_FROM_ALL)

    add_executable(madre-llamacpp-worker workers/llamacpp/main.cpp)
    target_include_directories(madre-llamacpp-worker PRIVATE src)
    target_link_libraries(madre-llamacpp-worker PRIVATE madre_kernel_protocol llama)
    madre_target_warnings(madre-llamacpp-worker)
endif()
