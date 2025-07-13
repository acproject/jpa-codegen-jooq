exe_path="mini_cache_server.exe"
dll_dir="msys_dlls"

rm -rf $dll_dir
mkdir -p $dll_dir

# 获取所有依赖 DLL 名称（仅保留 /mingw64 和 /usr/bin 下的）
dlls=$(ldd $exe_path | grep -E '/mingw64|/usr/bin' | awk '{print $3}')

for dll in $dlls; do
    cp "$dll" $dll_dir/
done

echo "✅ 所有依赖 DLL 已复制到 msys_dlls/"
