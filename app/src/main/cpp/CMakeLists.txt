cmake_minimum_required(VERSION 3.22.1)

project(BannerCloud)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

# DXVK's DXBC->SPIR-V translator (zlib licence), vendored from Bannerlator under thirdparty/dxbc.
# The renderer's LSFG engine links it; the app does not offer LSFG yet, but the renderer is
# copied whole from Bannerlator so its frame generation code stays identical.
add_subdirectory(thirdparty/dxbc)

# --- WFG (WinNative/Winlator Frame Generator, qwertypower / DEVAR Entertainment LLC, MIT) ---
# Copied from Bannerlator (which copied it unmodified from WinNative 72d145bc): vkr_dis.c/.h,
# shaders, LICENSE.wfg; vk_dispatch.h, wfg_engine.{h,cpp} and gen/ are Bannerlator's.
set(WFG_UPSTREAM "WinNative 72d145bc")
add_library(wfg STATIC winlator/wfg/vkr_dis.c)
target_include_directories(wfg PRIVATE winlator/wfg winlator/wfg/gen)
target_compile_options(wfg PRIVATE -fvisibility=hidden)

# --- win-fg chain (The412Banner/win-fg v0.3.0, MIT), copied from Bannerlator ---
set(WINFG_UPSTREAM "v0.3.0")
set(WINFG_CHAIN_SRC
        winlator/winfg/framegen.cpp
        winlator/winfg/framegen.hpp
        winlator/winfg/record_impl.inc
        winlator/winfg/config.hpp
        winlator/winfg/embedded_shaders.hpp
        winlator/winfg/winfg_engine.cpp
)
set(WINFG_HASH_INPUT "")
foreach(f ${WINFG_CHAIN_SRC})
    file(SHA256 "${CMAKE_CURRENT_SOURCE_DIR}/${f}" _h)
    string(APPEND WINFG_HASH_INPUT "${_h}")
endforeach()
string(SHA256 WINFG_CHAIN_HASH_FULL "${WINFG_HASH_INPUT}")
string(SUBSTRING "${WINFG_CHAIN_HASH_FULL}" 0 12 WINFG_CHAIN_HASH)
message(STATUS "win-fg chain: ${WINFG_UPSTREAM} (src ${WINFG_CHAIN_HASH})")

# The stream player's renderer: Bannerlator's Vulkan compositor (one full-screen window fed with
# decoded video frames as AHardwareBuffers) with its frame generation engines.
add_library(vulkan_renderer SHARED
        winlator/stream_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp
        winlator/lsfg/lsfg_probe.cpp
        winlator/lsfg/lsfg_dll.cpp
        winlator/lsfg/lsfg_dxbc.cpp
        winlator/lsfg/lsfg_vkd.cpp
        winlator/lsfg/lsfg_common.cpp
        winlator/lsfg/lsfg_shaders.cpp
        winlator/lsfg/lsfg_mipmaps.cpp
        winlator/lsfg/lsfg_alpha.cpp
        winlator/lsfg/lsfg_beta.cpp
        winlator/lsfg/lsfg_gamma.cpp
        winlator/lsfg/lsfg_delta.cpp
        winlator/lsfg/lsfg_generate.cpp
        winlator/lsfg/lsfg_chain.cpp
        winlator/lsfg/lsfg_pacer.cpp
        winlator/lsfg/lsfg_engine.cpp
        winlator/lsfg/lsfg_governor.cpp
        winlator/winfg/framegen.cpp
        winlator/winfg/winfg_vkd.cpp
        winlator/winfg/winfg_engine.cpp
        winlator/wfg/wfg_engine.cpp
        scanout/ScanoutContext.cpp
)

target_compile_definitions(vulkan_renderer PRIVATE
        WINFG_UPSTREAM="${WINFG_UPSTREAM}"
        WFG_UPSTREAM="${WFG_UPSTREAM}"
        WINFG_CHAIN_HASH="${WINFG_CHAIN_HASH}"
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        nativewindow
        vulkan
        dxbc
        wfg
        dl
        atomic
)
