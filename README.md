# LOG empresas

Sistema de logística para operação com carretas de **piso móvel**: carga e descarga de cavaco, bagaço e outras biomassas, pedido de nota fiscal, checklist de caminhão e carreta e registro de ponto. Interface azul e amarela, pensada para celular e computador.

> Software proprietário. Todos os direitos reservados (veja [LICENSE](LICENSE)).

## Demonstração no navegador

O arquivo `index.html` funciona sozinho, sem servidor e sem instalação: dê dois cliques ou publique no GitHub Pages. Ele simula o servidor dentro do navegador, então os dados ficam só no aparelho e o login não tem segurança real.

Entrada rápida pelos botões da tela de login, ou `admin` / `Admin@2026` e `motorista` / `Motor@2026`.

### Publicar no GitHub Pages

1. Envie estes arquivos para o repositório (branch `main`).
2. Em **Settings → Pages**, escolha **Deploy from a branch**, branch `main`, pasta `/ (root)` e salve.
3. Em 1 a 2 minutos o endereço aparece na mesma página: `https://SEU-USUARIO.github.io/NOME-DO-REPOSITORIO/`.

## Versão completa (servidor Java)

Pasta `servidor/`. Usa somente o JDK, sem bibliotecas de terceiros.

- **Requisito:** JDK 17 ou mais novo. O Java do site java.com é a versão 8 e não serve.
- **Windows:** dois cliques em `servidor/INICIAR.bat`.
- **Outros sistemas:** `cd servidor` e `java LogEmpresas.java`.
- Abra `http://localhost:8080`.
- Variáveis opcionais: `LOG_PORTA`, `LOG_SENHA_ADMIN`, `LOG_SENHA_MOTORISTA`.
- Os dados ficam em `servidor/dados.tsv` (ignorado pelo Git).

## Módulos

- **Cargas e descargas:** cavaco, bagaço, serragem, casca e outros; placas do caminhão e da carreta, motorista, origem, destino e peso; status agendada, em andamento, concluída ou cancelada.
- **Nota fiscal:** pedido ligado a uma carga; o administrador marca como emitida ou cancela.
- **Checklist:** 12 itens (6 do caminhão e 6 da carreta de piso móvel); qualquer item sem marca reprova.
- **Ponto:** entrada, início e fim de intervalo e saída, em sequência obrigatória, com horário do servidor.
- **Painel:** cargas em aberto, notas pendentes, checklists reprovados e toneladas concluídas.

## Segurança da versão servidor

- Senhas com PBKDF2-SHA256 (120 mil iterações) e sal individual.
- Sessão em cookie HttpOnly e SameSite=Strict, com expiração de 30 minutos sem uso.
- Bloqueio de 5 minutos após 5 senhas erradas.
- Proteção contra CSRF, cabeçalhos CSP e anti-iframe, validação de todos os campos no servidor.
- Perfis: administrador e motorista.

Antes de usar de verdade: troque as senhas padrão pelas variáveis acima e publique atrás de HTTPS.

## Roteiro de homologação

1. Senha errada mostra erro; 5 erros seguidos bloqueiam por 5 minutos.
2. Cargas: placa inválida (`123`) é recusada; `ABC1D23` é aceita.
3. Cargas: Iniciar e Concluir mudam o status; o Painel soma as toneladas concluídas.
4. Notas: escolha uma carga e solicite; como admin, marque emitida; como motorista, o botão não aparece.
5. Checklist: tudo marcado = Aprovado; item sem marca = Reprovado, com o item em vermelho.
6. Ponto: só fica ativo o próximo registro; o horário vem do servidor.
7. Depois de Sair, abrir `/api/cargas` deve ser negado.
8. Reiniciar o servidor mantém os dados.

Teste automático da demonstração: `node tests/demo.test.js`.

## Estrutura

```
index.html        demonstração (GitHub Pages)
servidor/         versão completa em Java + interface web
tests/            teste automático da demonstração
LICENSE           licença proprietária
```
