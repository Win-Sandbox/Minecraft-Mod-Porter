cmake_minimum_required(VERSION 3.10)
project(ModPorterWin32 CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(ModPorter WIN32
    main.cpp
    util.cpp
    settings.cpp
    backend.cpp
    json.cpp
    page_convert.cpp
    page_report.cpp
    page_extensions.cpp
    page_settings.cpp
    ModPorter.rc
)

target_compile_definitions(ModPorter PRIVATE UNICODE _UNICODE)

if (MSVC)
    # /MT 静态链接 CRT：生成的 exe 在没有 VC++ 运行库的旧机器上也能直接跑
    # /utf-8 必需：源码含中文字面量，缺此选项时 MSVC 会按系统 ANSI 代码页解析导致乱码
    set_property(TARGET ModPorter PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
    target_compile_options(ModPorter PRIVATE /W4 /utf-8)
else()
    # MinGW：静态链接 libstdc++/libgcc 与线程库，同样为了免运行库依赖
    # 不开 -Wextra：Win32 结构体聚合初始化会刷屏 -Wmissing-field-initializers
    target_compile_options(ModPorter PRIVATE -Wall -municode
                           -finput-charset=UTF-8 -fexec-charset=UTF-8)
    target_link_options(ModPorter PRIVATE -municode -static -static-libgcc -static-libstdc++)
endif()

target_link_libraries(ModPorter PRIVATE comctl32 shell32 comdlg32 ole32 user32 gdi32 advapi32)
