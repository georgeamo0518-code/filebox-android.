FILEBOX - COMPILACAO DO APK NA NUVEM

Este pacote contem o projeto Android e um workflow do GitHub Actions que compila
um APK debug na nuvem e disponibiliza o APK como artefato para download.

PASSOS (uma unica configuracao inicial):
1. Entre em https://github.com e crie/entre na sua conta.
2. Crie um repositorio novo, por exemplo: filebox-android.
3. Extraia este ZIP no computador.
4. Envie TODOS os arquivos e pastas que estao dentro da pasta file-manager-clone
   para a raiz do repositorio GitHub. Inclua a pasta oculta .github.
5. Na pagina do repositorio, abra a aba Actions.
6. Se aparecer uma tela para habilitar Actions, habilite.
7. Selecione "Build Filebox APK" e clique em "Run workflow" (ou aguarde o build
   automatico apos enviar os arquivos para a branch main).
8. Quando o workflow ficar verde (sucesso), abra a execucao concluida e baixe
   o artefato "Filebox-debug-APK". Dentro dele estara o APK para instalar.

OBSERVACOES:
- O APK debug e para teste/instalacao manual; nao e uma versao assinada para Play Store.
- A compilacao ainda precisa ser confirmada pelo GitHub Actions. Este ambiente nao
  tem acesso externo para baixar Gradle/Android SDK e nao consegue validar o build local.
- Se a execucao falhar, abra o log da etapa que ficou vermelha e envie o texto/print
  para corrigirmos o erro restante.
