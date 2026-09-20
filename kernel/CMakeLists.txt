cmake_minimum_required(VERSION 3.20)
project(madre_kernel LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SQLite3 REQUIRED)
find_package(Threads REQUIRED)

add_library(madre_kernel_protocol STATIC src/protocol.cpp)
target_include_directories(madre_kernel_protocol PUBLIC src)
target_compile_options(madre_kernel_protocol PRIVATE -Wall -Wextra -Wpedantic -Werror)

add_executable(madre-kernel src/main.cpp src/store.cpp)
target_include_directories(madre-kernel PRIVATE src)
target_link_libraries(madre-kernel PRIVATE madre_kernel_protocol SQLite::SQLite3 Threads::Threads)
target_compile_options(madre-kernel PRIVATE -Wall -Wextra -Wpedantic -Werror)

enable_testing()
add_executable(protocol-test tests/protocol_test.cpp)
target_link_libraries(protocol-test PRIVATE madre_kernel_protocol Threads::Threads)
target_compile_options(protocol-test PRIVATE -Wall -Wextra -Wpedantic -Werror)
add_test(NAME protocol-round-trip COMMAND protocol-test)
