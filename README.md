# FFProxy

Proxy HTTP/HTTPS local com MITM e reescrita de resposta, feito para liberar
120 FPS, HFR e afins no Free Fire - com um switch individual por funcao.

Roda 100% no celular, sem root.

## Compilar

### Opcao A - Android Studio
1. Open -> pasta FFProxy
2. Aceite o sync
3. Build > Build APK(s)

### Opcao B - sem Android Studio
Faca um push. A aba Actions compila e publica o APK como artifact.

minSdk 24 | targetSdk 34 | Java 17

Se o Gradle reclamar de slf4j-android, troque por
implementation("org.slf4j:slf4j-simple:1.7.36").

## Usar
1. Abra o FFProxy, ligue "Ligar proxy", autorize a notificacao
2. Anote o IP mostrado
3. Exporte o certificado CA e instale em
   Configuracoes > Seguranca > Criptografia e credenciais > Instalar certificado > CA
4. Wi-Fi > sua rede > Editar > Avancado > Proxy Manual
   Host = IP mostrado, Porta = 8080
5. Abra o Free Fire

## Modulos
fps120, fps90, hfr, gfx, aim, nocache, injectjs.
Toque no nome de qualquer modulo para editar a regex e o valor.

## Limites
Funciona via proxy: FPS, HFR, qualidade, headers, injecao de JS.
Nao funciona via proxy: aimbot com lock, ESP, wallhack, no recoil.

## MITM
Android 7+ nao confia em CA de usuario em apps que nao declararam isso.
Se o Free Fire rejeitar: desligue o MITM, ou use root + Magisk.