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

add_executable(madre-kernel
    src/main.cpp
    src/store.cpp
    src/process_executor.cpp
    src/local_ipc.cpp)
target_include_directories(madre-kernel PRIVATE src)
target_link_libraries(madre-kernel PRIVATE madre_kernel_protocol SQLite::SQLite3 Threads::Threads)
madre_target_warnings(madre-kernel)

add_executable(madre-process-fixture tests/process_fixture.cpp)
target_link_libraries(madre-process-fixture PRIVATE Threads::Threads)
madre_target_warnings(madre-process-fixture)

enable_testing()
add_executable(protocol-test tests/protocol_test.cpp)
target_link_libraries(protocol-test PRIVATE madre_kernel_protocol)
madre_target_warnings(protocol-test)
add_test(NAME protocol-round-trip COMMAND protocol-test)
