# app_llm

Aplicación Quarkus de Hack4Edu: API REST, interfaz web y asistente (LangChain4j + Ollama).
Documentación general en el [README de la raíz](../README.md) y estructura del código en
[docs/arquitectura.md](../docs/arquitectura.md).

```bash
./mvnw quarkus:dev                 # desarrollo con recarga (necesita la base de datos: ver README de la raíz)
./mvnw test                        # tests unitarios (no necesitan base de datos)
./mvnw package                     # genera target/quarkus-app/
# imagen única (PostgreSQL + app): desde la raíz, docker compose up -d --build
```

## Variables de entorno

| Variable | Para qué | Por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/escuela` |
| `ESCUELA_ADMIN_PASSWORD` | Migraciones Flyway | — |
| `APP_LECTOR_PASSWORD` · `APP_INGESTA_PASSWORD` | Conexiones de lectura y de ingesta | — |
| `DOCENTE_PASSWORD` · `DIRECCION_PASSWORD` | Usuarios de la aplicación | — |
| `OLLAMA_URL` | Servidor de Ollama | `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` · `OLLAMA_MODELO_RAPIDO` · `OLLAMA_MODELO_PRECISO` | Modelos de chat | `llama3.2:3b` · `llama3.2:1b` · `qwen3.5:4b` |
| `OLLAMA_EMBEDDING_MODEL` · `RAG_EMBEDDING_DIM` | Embeddings | `embeddinggemma` · `768` |
