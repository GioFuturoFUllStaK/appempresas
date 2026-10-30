// LOG empresas - logística de piso móvel (cavaco, bagaço e biomassa)
// © LOG empresas. Todos os direitos reservados. Software proprietário: somente JDK 17+, sem bibliotecas de terceiros.
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class LogEmpresas {
  record Usuario(String nome, String perfil, byte[] sal, byte[] hash) {}
  record Sessao(String nome, String perfil, long[] exp) {}
  static final long TTL = 30 * 60_000L;
  static final Map<String, Usuario> USUARIOS = new HashMap<>();
  static final Map<String, Sessao> SESSOES = new ConcurrentHashMap<>();
  static final Map<String, long[]> FALHAS = new ConcurrentHashMap<>();
  static final Map<String, List<Map<String, String>>> DB = new HashMap<>();
  static final Map<String, String[]> CAMPOS = Map.of(
      "cargas", new String[]{"operacao", "produto", "placa", "carreta", "motorista", "origem", "destino", "peso"},
      "notas", new String[]{"carga", "cliente", "produto", "peso"},
      "checklists", new String[]{"placa", "carreta", "motorista", "itens", "obs"},
      "pontos", new String[]{"tipo"});
  static final Map<String, Set<String>> ENUM = Map.of(
      "operacao", Set.of("CARGA", "DESCARGA"),
      "produto", Set.of("CAVACO", "BAGACO", "SERRAGEM", "CASCA", "OUTRO"),
      "tipo", Set.of("ENTRADA", "INI_INTERVALO", "FIM_INTERVALO", "SAIDA"));
  static final Pattern PLACA = Pattern.compile("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"), PESO = Pattern.compile("\\d{1,2}(\\.\\d{1,3})?"), ITENS = Pattern.compile("[01]{12}");
  static final SecureRandom RND = new SecureRandom();
  static final Path ARQ = Path.of("dados.tsv");
  static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  static long seq = 0;

  public static void main(String[] a) throws Exception {
    usuario("admin", "ADMIN", env("LOG_SENHA_ADMIN", "Admin@2026"));
    usuario("motorista", "MOTORISTA", env("LOG_SENHA_MOTORISTA", "Motor@2026"));
    for (String c : CAMPOS.keySet()) DB.put(c, new ArrayList<>());
    carregar();
    int porta = Integer.parseInt(env("LOG_PORTA", "8080"));
    HttpServer s = HttpServer.create(new InetSocketAddress(porta), 0);
    s.createContext("/", LogEmpresas::tratar);
    s.setExecutor(Executors.newFixedThreadPool(8));
    s.start();
    System.out.println("LOG empresas no ar em http://localhost:" + porta);
  }

  static String env(String k, String v) { String x = System.getenv(k); return x == null || x.isBlank() ? v : x; }
  static IllegalArgumentException erro(String m) { return new IllegalArgumentException(m); }
  static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
  static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

  static byte[] hash(String s, byte[] sal) throws Exception {
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(s.toCharArray(), sal, 120_000, 256)).getEncoded();
  }
  static void usuario(String nome, String perfil, String senha) throws Exception {
    byte[] sal = new byte[16]; RND.nextBytes(sal);
    USUARIOS.put(nome, new Usuario(nome, perfil, sal, hash(senha, sal)));
  }

  static synchronized void salvar() throws IOException {
    StringBuilder b = new StringBuilder();
    DB.forEach((c, l) -> l.forEach(r -> { b.append(c); r.forEach((k, v) -> b.append('\t').append(enc(k)).append('=').append(enc(v))); b.append('\n'); }));
    Files.writeString(ARQ, b.toString());
  }
  static void carregar() throws IOException {
    if (!Files.exists(ARQ)) return;
    for (String ln : Files.readAllLines(ARQ)) {
      String[] p = ln.split("\t"); Map<String, String> r = new LinkedHashMap<>();
      for (int i = 1; i < p.length; i++) { String[] kv = p[i].split("=", 2); r.put(dec(kv[0]), dec(kv.length > 1 ? kv[1] : "")); }
      DB.get(p[0]).add(r); seq = Math.max(seq, Long.parseLong(r.get("id")));
    }
  }

  static void tratar(HttpExchange x) throws IOException {
    try {
      String p = x.getRequestURI().getPath(), m = x.getRequestMethod();
      Headers h = x.getResponseHeaders();
      h.set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
      h.set("X-Content-Type-Options", "nosniff"); h.set("X-Frame-Options", "DENY");
      h.set("Referrer-Policy", "no-referrer"); h.set("Cache-Control", "no-store");
      if (!m.equals("GET") && !m.equals("POST")) { json(x, 405, "{\"erro\":\"Método não permitido.\"}"); return; }
      if (!p.startsWith("/api/")) { estatico(x, p); return; }
      if (m.equals("POST") && !"1".equals(x.getRequestHeaders().getFirst("X-LOG"))) { json(x, 403, "{\"erro\":\"Requisição inválida.\"}"); return; }
      Map<String, String> f = m.equals("POST") ? form(x) : Map.of();
      String r = p.substring(5);
      if (r.equals("login")) { login(x, f); return; }
      Sessao s = sessao(x);
      if (s == null) { json(x, 401, "{\"erro\":\"Sessão expirada. Entre novamente.\"}"); return; }
      if (r.equals("sair")) { SESSOES.values().remove(s); cookie(x, "", 0); json(x, 200, "{}"); return; }
      if (r.equals("eu")) { json(x, 200, "{\"nome\":" + js(s.nome()) + ",\"perfil\":\"" + s.perfil() + "\"}"); return; }
      api(x, m, r.split("/"), f, s);
    } catch (IllegalArgumentException e) { json(x, 400, "{\"erro\":" + js(e.getMessage()) + "}");
    } catch (Exception e) { e.printStackTrace(); json(x, 500, "{\"erro\":\"Erro interno.\"}"); }
  }

  static void login(HttpExchange x, Map<String, String> f) throws Exception {
    String u = f.getOrDefault("u", "").toLowerCase(), k = x.getRemoteAddress().getAddress().getHostAddress() + "|" + u;
    long agora = System.currentTimeMillis(); long[] fl = FALHAS.computeIfAbsent(k, z -> new long[2]);
    if (fl[1] > agora) { json(x, 429, "{\"erro\":\"Acesso bloqueado por 5 minutos após 5 tentativas erradas.\"}"); return; }
    Usuario us = USUARIOS.get(u);
    byte[] calc = hash(f.getOrDefault("s", ""), us == null ? new byte[16] : us.sal());
    if (us == null || !MessageDigest.isEqual(calc, us.hash())) {
      if (++fl[0] >= 5) { fl[0] = 0; fl[1] = agora + 300_000; }
      Thread.sleep(400); json(x, 401, "{\"erro\":\"Usuário ou senha incorretos.\"}"); return;
    }
    FALHAS.remove(k);
    byte[] t = new byte[32]; RND.nextBytes(t); String id = HexFormat.of().formatHex(t);
    SESSOES.put(id, new Sessao(us.nome(), us.perfil(), new long[]{agora + TTL}));
    cookie(x, id, TTL / 1000);
    json(x, 200, "{\"nome\":" + js(us.nome()) + ",\"perfil\":\"" + us.perfil() + "\"}");
  }
  static void cookie(HttpExchange x, String v, long seg) { x.getResponseHeaders().add("Set-Cookie", "sid=" + v + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + seg); }
  static Sessao sessao(HttpExchange x) {
    String c = x.getRequestHeaders().getFirst("Cookie"); if (c == null) return null;
    for (String p : c.split(";\\s*")) if (p.startsWith("sid=")) {
      Sessao s = SESSOES.get(p.substring(4));
      if (s != null && s.exp()[0] > System.currentTimeMillis()) { s.exp()[0] = System.currentTimeMillis() + TTL; return s; }
      SESSOES.remove(p.substring(4));
    }
    return null;
  }

  static synchronized void api(HttpExchange x, String m, String[] r, Map<String, String> f, Sessao s) throws IOException {
    List<Map<String, String>> l = DB.get(r[0]);
    boolean adm = s.perfil().equals("ADMIN");
    if (l == null || (r.length != 1 && r.length != 3) || (m.equals("GET") && r.length != 1)) { json(x, 404, "{\"erro\":\"Não encontrado.\"}"); return; }
    if (m.equals("GET")) {
      StringBuilder b = new StringBuilder("["); int n = 0;
      for (int i = l.size() - 1; i >= 0 && n < 200; i--) {
        Map<String, String> d = l.get(i);
        if (r[0].equals("pontos") && !adm && !d.get("usuario").equals(s.nome())) continue;
        b.append(n++ > 0 ? "," : "").append(obj(d));
      }
      json(x, 200, b.append("]").toString()); return;
    }
    if (r.length == 3) {
      Set<String> ok = r[0].equals("cargas") ? Set.of("AGENDADA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA") : r[0].equals("notas") ? Set.of("PENDENTE", "EMITIDA", "CANCELADA") : Set.of();
      if (!ok.contains(r[2])) throw erro("Status inválido.");
      if (r[0].equals("notas") && !adm) throw erro("Somente o administrador altera notas.");
      for (Map<String, String> d : l) if (d.get("id").equals(r[1])) { d.put("status", r[2]); salvar(); json(x, 200, obj(d)); return; }
      throw erro("Registro não encontrado.");
    }
    Map<String, String> d = new LinkedHashMap<>();
    for (String c : CAMPOS.get(r[0])) d.put(c, valida(c, f.getOrDefault(c, "")));
    regras(r[0], d, s);
    d.put("id", String.valueOf(++seq)); d.put("usuario", s.nome());
    d.put("criado", LocalDateTime.now(ZoneId.of("America/Campo_Grande")).format(FMT));
    l.add(d); salvar(); json(x, 201, obj(d));
  }

  static String valida(String c, String v) {
    v = v.replaceAll("\\p{Cntrl}", " ").strip();
    if (v.isEmpty()) { if (c.equals("obs")) return ""; throw erro("Preencha o campo " + c + "."); }
    if (v.length() > (c.equals("obs") ? 200 : 60)) throw erro("Texto longo demais em " + c + ".");
    if (ENUM.containsKey(c) && !ENUM.get(c).contains(v)) throw erro("Valor inválido em " + c + ".");
    if (c.equals("placa") || c.equals("carreta")) {
      v = v.toUpperCase().replace("-", "");
      if (!PLACA.matcher(v).matches()) throw erro("Placa inválida em " + c + " (modelo ABC1D23).");
    }
    if (c.equals("peso")) {
      v = v.replace(',', '.');
      if (!PESO.matcher(v).matches() || Double.parseDouble(v) <= 0 || Double.parseDouble(v) > 60) throw erro("O peso deve ficar entre 0,1 e 60 toneladas.");
    }
    if (c.equals("itens") && !ITENS.matcher(v).matches()) throw erro("Responda os 12 itens do checklist.");
    return v;
  }

  static void regras(String c, Map<String, String> d, Sessao s) {
    switch (c) {
      case "cargas" -> d.put("status", "AGENDADA");
      case "notas" -> {
        if (DB.get("cargas").stream().noneMatch(k -> k.get("id").equals(d.get("carga")))) throw erro("Carga não encontrada.");
        d.put("status", "PENDENTE");
      }
      case "checklists" -> d.put("status", d.get("itens").contains("0") ? "REPROVADO" : "APROVADO");
      case "pontos" -> {
        String ult = "";
        for (Map<String, String> q : DB.get("pontos")) if (q.get("usuario").equals(s.nome())) ult = q.get("tipo");
        String ok = switch (ult) { case "ENTRADA" -> "INI_INTERVALO SAIDA"; case "INI_INTERVALO" -> "FIM_INTERVALO"; case "FIM_INTERVALO" -> "SAIDA"; default -> "ENTRADA"; };
        if (!Arrays.asList(ok.split(" ")).contains(d.get("tipo"))) throw erro("Registro fora de sequência. Confira o último ponto.");
      }
      default -> {}
    }
  }

  static Map<String, String> form(HttpExchange x) throws IOException {
    byte[] b = x.getRequestBody().readNBytes(4097);
    if (b.length > 4096) throw erro("Dados grandes demais.");
    Map<String, String> m = new HashMap<>();
    for (String kv : new String(b, StandardCharsets.UTF_8).split("&")) { String[] p = kv.split("=", 2); if (p.length == 2) m.put(dec(p[0]), dec(p[1]).strip()); }
    return m;
  }
  static void estatico(HttpExchange x, String p) throws IOException {
    String n = p.equals("/") ? "index.html" : p.substring(1);
    String t = switch (n) { case "index.html" -> "text/html"; case "app.js" -> "text/javascript"; case "app.css" -> "text/css"; default -> null; };
    Path f = Path.of("web", n);
    if (t == null || !Files.exists(f)) { json(x, 404, "{\"erro\":\"Não encontrado.\"}"); return; }
    saida(x, 200, t + "; charset=utf-8", Files.readAllBytes(f));
  }
  static void json(HttpExchange x, int c, String s) throws IOException { saida(x, c, "application/json; charset=utf-8", s.getBytes(StandardCharsets.UTF_8)); }
  static void saida(HttpExchange x, int c, String t, byte[] b) throws IOException {
    x.getResponseHeaders().set("Content-Type", t); x.sendResponseHeaders(c, b.length);
    try (OutputStream o = x.getResponseBody()) { o.write(b); }
  }
  static String obj(Map<String, String> d) {
    StringBuilder b = new StringBuilder("{"); int i = 0;
    for (var e : d.entrySet()) b.append(i++ > 0 ? "," : "").append(js(e.getKey())).append(':').append(js(e.getValue()));
    return b.append('}').toString();
  }
  static String js(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) b.append(c == '"' || c == '\\' ? "\\" + c : c < 32 ? " " : String.valueOf(c));
    return b.append('"').toString();
  }
}
