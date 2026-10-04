#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# XaulinXs Foundry — setup de build 100% LOCAL no Termux, sem GitHub Actions
# ============================================================================
#
# O que estava quebrando: cmake/ninja/clang baixados automaticamente pelo AGP
# (dentro de android-sdk/cmake/... e android-sdk/ndk/...) sao binarios Linux
# x86_64 do Google. O Termux roda em ARM (aarch64) - por isso o cmake
# "parecia" um script de texto e dava erro de sintaxe.
#
# Este script resolve os TRES pontos com ferramentas ARM nativas, SEM
# alterar nada dentro do repositorio (build.gradle continua pedindo NDK
# 28.2.13676358 e cmake 3.22.1 - o GitHub Actions continua funcionando
# normalmente quando voce publicar):
#
#   1) cmake e ninja -> pacotes do proprio Termux (pkg), ja ARM
#   2) NDK            -> build de terceiros (lzhiyong/termux-ndk, r29, ARM),
#                        "disfarcado" de versao 28.2.13676358 dentro do SDK
#                        local, que e a versao que o build.gradle do projeto
#                        pede (o AGP SO aceita a pasta se o source.properties
#                        dentro dela declarar essa versao exata - por isso
#                        reescrevemos esse arquivo, o resto do NDK r29 e
#                        usado sem alteracao)
#   3) aapt2           -> pacote do Termux (pkg), evita o aapt2 x86 do AGP
#
# Roda UMA VEZ (ou de novo sem problema, e idempotente). Depois disso
# "./gradlew assembleDebug" funciona direto, sem internet nem GitHub.
#
# Uso:
#   cd ~/XaulinXs-LatinIME-main   (pasta do repo clonado)
#   bash setup-termux-build.sh
# ============================================================================
set -e

NDK_WANTED_VERSION="28.2.13676358"   # o que build.gradle (ndkVersion) pede
NDK_ARCHIVE="android-ndk-r29-aarch64.tar.xz"
NDK_URL="https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/${NDK_ARCHIVE}"
NDK_REAL_DIR="${HOME}/.xaulinxs-ndk-r29-real"

echo "== 1/5: pacotes Termux (cmake, ninja, aapt2, clang, wget) =="
pkg update -y
pkg install -y cmake ninja aapt2 clang wget

echo "== 2/5: baixando NDK r29 nativo ARM (lzhiyong/termux-ndk), se preciso =="
if [ -d "${NDK_REAL_DIR}" ] && [ -f "${NDK_REAL_DIR}/source.properties" ]; then
  echo "   ja baixado em ${NDK_REAL_DIR}, pulando download"
else
  rm -rf "${NDK_REAL_DIR}"
  mkdir -p "${NDK_REAL_DIR}"
  cd "${HOME}"
  wget --tries=20 --retry-connrefused --waitretry=5 -O "${NDK_ARCHIVE}" "${NDK_URL}"
  tar xf "${NDK_ARCHIVE}" -C "${NDK_REAL_DIR}" --strip-components=1
  rm -f "${NDK_ARCHIVE}"
  if [ ! -f "${NDK_REAL_DIR}/source.properties" ]; then
    echo "AVISO: source.properties nao encontrado apos extrair — verificando estrutura..."
    find "${NDK_REAL_DIR}" -maxdepth 2 -iname "source.properties"
  fi
fi

echo "== 3/5: localizando o Android SDK do projeto =="
# Tenta os locais mais comuns; se o seu SDK estiver em outro lugar, exporte
# ANDROID_SDK_ROOT antes de rodar este script.
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME}}"
if [ -z "$SDK_ROOT" ]; then
  for cand in "${HOME}/android-sdk" "${HOME}/Android/Sdk" "${HOME}/android/sdk"; do
    if [ -d "$cand" ]; then SDK_ROOT="$cand"; break; fi
  done
fi
if [ -z "$SDK_ROOT" ]; then
  echo "ERRO: nao encontrei o Android SDK automaticamente."
  echo "Rode de novo assim:  ANDROID_SDK_ROOT=/caminho/do/sdk bash setup-termux-build.sh"
  exit 1
fi
echo "   SDK_ROOT = ${SDK_ROOT}"

echo "== 4/5: 'disfarcando' o NDK r29 real como ${NDK_WANTED_VERSION} =="
NDK_FAKE_DIR="${SDK_ROOT}/ndk/${NDK_WANTED_VERSION}"
rm -rf "${NDK_FAKE_DIR}"
mkdir -p "$(dirname "${NDK_FAKE_DIR}")"
# Symlink em vez de copia: instantaneo e nao duplica espaco em disco.
ln -s "${NDK_REAL_DIR}" "${NDK_FAKE_DIR}"
# O AGP valida a pasta pelo Pkg.Revision dentro de source.properties - por
# isso reescrevemos esse arquivo (dentro do diretorio REAL, nao do symlink)
# para declarar a versao que o build.gradle do projeto espera. O resto do
# NDK r29 (clang, sysroot, etc.) e usado sem nenhuma alteracao.
cat > "${NDK_REAL_DIR}/source.properties" <<EOF
Pkg.Desc = Android NDK (build Termux ARM, disfarcado para ${NDK_WANTED_VERSION})
Pkg.Revision = ${NDK_WANTED_VERSION}
EOF
echo "   ${NDK_FAKE_DIR} -> ${NDK_REAL_DIR} (source.properties ajustado)"

echo "== 5/5: cmake/ninja ARM no caminho que o AGP procura + aapt2 override =="
CMAKE_DEST="${SDK_ROOT}/cmake/3.22.1/bin"
mkdir -p "${CMAKE_DEST}"
for bin in cmake ninja ctest cpack; do
  SRC="${PREFIX}/bin/${bin}"
  if [ -e "$SRC" ]; then
    ln -sf "$SRC" "${CMAKE_DEST}/${bin}"
  fi
done

mkdir -p "${HOME}/.gradle"
GP="${HOME}/.gradle/gradle.properties"
touch "$GP"
grep -v "android.aapt2FromMavenOverride\|# XaulinXs Foundry: build Termux ARM" "$GP" > "${GP}.tmp" 2>/dev/null || true
mv "${GP}.tmp" "$GP"
cat >> "$GP" <<EOF
# XaulinXs Foundry: build Termux ARM (gerado por setup-termux-build.sh)
android.aapt2FromMavenOverride=${PREFIX}/bin/aapt2
EOF

echo ""
echo "=========================================================="
echo " Pronto. Dentro da pasta do projeto, rode:"
echo ""
echo "   ./gradlew assembleDebug --no-daemon"
echo ""
echo " Nada no repositorio (build.gradle, settings.gradle, workflow) foi"
echo " alterado - o GitHub Actions continua funcionando normalmente se/"
echo " quando voce der push."
echo ""
echo " Se o SDK_ROOT detectado (${SDK_ROOT}) estiver errado, rode de novo:"
echo "   ANDROID_SDK_ROOT=/caminho/certo bash setup-termux-build.sh"
echo "=========================================================="
