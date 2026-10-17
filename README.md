# PediuPatrão

Sistema de pedidos de pizzaria (Java 21, Spring Boot 3, Spring Security, MongoDB e Thymeleaf), evoluído
para a Atividade Prática M1 – Parte 2: perfis de acesso, ciclo de vida dos pedidos, descontos e
cancelamentos exclusivos do gerente e auditoria persistente.

## Como executar

### Pré-requisitos
- JDK 21 ou mais recente. Com JDK 24+, é necessário o Lombok 1.18.42 ou superior.
- Acesso a um banco MongoDB (local ou Atlas).

### Configurar a conexão com o MongoDB
A string de conexão não fica no código. Ela é lida da variável de ambiente `MONGODB_URI`
(`src/main/resources/application.properties`):

```properties
spring.data.mongodb.uri=${MONGODB_URI}
spring.data.mongodb.database=pizzaria
```

O usuário e a senha do banco vão dentro da própria URI (`mongodb+srv://usuario:senha@host/...`).

- **IntelliJ:** abra **Run → Edit Configurations → PizzariaPedidosApplication → Environment variables** e adicione
  `MONGODB_URI=mongodb+srv://<usuario>:<senha>@<cluster>/?appName=Cluster0`.
- **Terminal (Windows PowerShell):**
  ```powershell
  $env:MONGODB_URI="mongodb+srv://<usuario>:<senha>@<cluster>/?appName=Cluster0"
  .\mvnw.cmd spring-boot:run
  ```
- **Terminal (Linux/macOS):**
  ```bash
  export MONGODB_URI="mongodb+srv://<usuario>:<senha>@<cluster>/?appName=Cluster0"
  ./mvnw spring-boot:run
  ```

Se a variável não estiver definida, a aplicação não inicia e mostra o erro
`Connection strings must start with either 'mongodb://' or 'mongodb+srv://'`.

### Acessar
Abra http://localhost:8080. Na primeira execução é criado o usuário `admin` / `teste123` com perfil ADMIN.
Pelo menu **Configurações → Usuários**, o ADMIN cadastra os usuários com perfil GERENTE e ATENDENTE.
O botão **Sair**, no fim do menu lateral, encerra a sessão.

### Testes
```bash
./mvnw test        # Windows: mvnw.cmd test
```
Os testes não precisam do MongoDB.

## Requisitos implementados

### RF01 – Usuários e permissões
- Perfis `ADMIN`, `GERENTE` e `ATENDENTE` (campo `role` do usuário). O `UsuarioDetailsService` passou a usar o perfil gravado, em vez de `USER` fixo.
- Autorização no backend em duas camadas: regras por rota no `SecurityConfig` e `@PreAuthorize` no `PedidoService` (desconto e cancelamento). Esconder botões não é usado como proteção; as telas mostram as ações e o backend devolve "Acesso negado" (403).
- `/api/usuarios` deixou de ser público: usuários e perfis são administrados apenas pelo ADMIN.
- O `UsuarioService` só aceita os três perfis válidos e impede que o usuário logado altere o próprio perfil (autoelevação).
- CSRF reativado. Os formulários Thymeleaf enviam o token automaticamente, e as chamadas `fetch` enviam o token pelo header (meta tags no `layout.html`). O logout também é feito por POST com o token.

| Operação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e perfis | ✅ | | |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar pedidos e alterar status | | ✅ | ✅ |
| Aplicar desconto / cancelar pedido | | ✅ | |
| Consultar clientes | | ✅ | ✅ |
| Cadastrar e atualizar clientes | | | ✅ |
| Consultar produtos | ✅ | ✅ | ✅ |
| Cadastrar, alterar e excluir produtos | ✅ | ✅ | |
| Consultar auditoria | ✅ | ✅ | |

O enunciado não define quem mantém o cadastro de produtos. A escolha foi ADMIN e GERENTE porque o preço do
produto é usado no cálculo do valor dos pedidos, e o ATENDENTE não deve conseguir alterá-lo.

### RF02 – Pedido
O `Pedido` recebeu itens (`ItemPedido`: produto, preço unitário, quantidade e subtotal), percentual e valor de desconto,
valor total, responsável pelo registro e data/hora de entrada. Na criação, o servidor usa da requisição apenas o cliente,
os produtos e as quantidades. Preços, total, status, responsável e data são definidos no `PedidoService`, e a
requisição não consegue definir o total.

### RF03 – Entrada, saída e transições
O enum `StatusPedido` define o fluxo RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ou RETIRADO → FINALIZADO.
Transições fora do fluxo, como finalizar um pedido em preparação, são recusadas, e nenhuma alteração de status é aceita
após o cancelamento. Na saída, são registrados o responsável e a data/hora.

### RF04 – Cancelamento e desconto (somente GERENTE)
- Cancelamento: justificativa obrigatória e permitido apenas antes da saída. Os dados do pedido são preservados.
- Desconto: percentual maior que 0 e no máximo 20%, permitido apenas antes da saída. Um novo desconto substitui o anterior, sem acumular.

### RF05 – Auditoria
- Entidade `Auditoria` (coleção `auditoria`), com `AuditoriaRepository` e `AuditoriaService` próprios.
- Cada registro guarda o usuário, o perfil, a data/hora, a operação, a entidade e seu id, os campos alterados (valor anterior e novo valor) e a justificativa, quando houver.
- São auditados: inclusão de pedido, mudança de status, desconto, cancelamento e inclusão, alteração e exclusão de cliente.
- O `AuditoriaRepository` estende `Repository`, e não `MongoRepository`, por isso só tem inclusão e consulta, sem exclusão ou alteração. A API `/api/auditoria` e a tela **Auditoria** são somente leitura e restritas a ADMIN e GERENTE.
- Só os campos que mudaram são gravados, e senhas ou credenciais nunca são registradas.

### Cadastro de produtos
As telas de produtos existentes não funcionavam: a rota carregava um fragmento que não existia no template.
O cadastro foi refeito no mesmo padrão de clientes e usuários, com listagem, criação, edição e exclusão. O preço precisa
ser maior que zero, e só produtos ativos com preço podem entrar em pedidos.

## Endpoints da API alterados ou criados

| Método | Rota | Perfil |
|---|---|---|
| POST | `/api/pedidos` | ATENDENTE, GERENTE |
| PUT | `/api/pedidos/{id}/status?status=EM_PREPARACAO` | ATENDENTE, GERENTE |
| POST | `/api/pedidos/{id}/desconto?percentual=10` | GERENTE |
| POST | `/api/pedidos/{id}/cancelar?justificativa=...` | GERENTE |
| GET | `/api/auditoria` | ADMIN, GERENTE |
| GET/POST/PUT/DELETE | `/api/usuarios/**` | ADMIN |

Violações de regra de negócio na API de pedidos retornam 400 com a mensagem; falta de permissão retorna 403.

### Demonstrando o bloqueio na API
Logado como **admin** ou **atendente**, abra o console do navegador (F12) em qualquer página do sistema e execute,
trocando `ID_DO_PEDIDO` pelo id de um pedido:

```javascript
fetch('/api/pedidos/ID_DO_PEDIDO/desconto?percentual=10', {
  method: 'POST',
  headers: { [document.querySelector('meta[name="_csrf_header"]').content]:
             document.querySelector('meta[name="_csrf"]').content }
}).then(r => console.log('Status:', r.status));   // 403
```

## Principais alterações
- **Configuração e segurança:** `SecurityConfig`, `UsuarioDetailsService`, `UsuarioService`, `DataInitializer` e `application.properties` (URI do MongoDB em variável de ambiente).
- **Pedidos:** `Pedido`, mais as novas classes `ItemPedido` e `StatusPedido`, além de `PedidoService` e `PedidoController`.
- **Clientes:** `ClienteService`, que agora registra a auditoria.
- **Auditoria:** novas classes `Auditoria`, `AuditoriaRepository`, `AuditoriaService` e `AuditoriaController`.
- **Produtos:** `ProdutoService` (busca por id e validação de preço) e novas rotas no `HomeController`.
- **Telas:** `pedidos.html` (ações de status, desconto e cancelamento), `pedidos/form.html`, `auditoria.html`, `produtos/lista.html`, `produtos/produto-form.html`, `fragments/sidebar.html` (Auditoria e botão Sair), `layout.html` (token CSRF), `clientes/lista.html` (token CSRF na exclusão) e `usuarios/form.html` (os três perfis).
- **Build:** `pom.xml` com Lombok 1.18.42 e dependências de teste.
- **Testes:** `PedidoServiceTest` (regras de negócio) e `SegurancaTest` (bloqueios por perfil e CSRF nas APIs).
