CREATE TABLE sessao_cadastro (
    id               UUID PRIMARY KEY,
    segredo_hash     VARCHAR(64) NOT NULL UNIQUE,
    estado           VARCHAR(30) NOT NULL,
    modo_interacao   VARCHAR(20) NOT NULL,
    versao           INTEGER     NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ NOT NULL,
    ultimo_acesso_em TIMESTAMPTZ NOT NULL,
    expira_em        TIMESTAMPTZ NOT NULL,
    CONSTRAINT sessao_estado_valido CHECK (estado IN (
        'EM_PREENCHIMENTO', 'AGUARDANDO_ESCLARECIMENTO', 'EM_REVISAO',
        'CONFIRMADO', 'CANCELADO', 'EXPIRADO')),
    CONSTRAINT sessao_modo_valido CHECK (modo_interacao IN ('ASSISTIDO_POR_IA', 'ESTRUTURADO'))
);

CREATE INDEX sessao_cadastro_expira_em_idx ON sessao_cadastro (expira_em);

CREATE TABLE rascunho_cadastro (
    sessao_id     UUID PRIMARY KEY REFERENCES sessao_cadastro (id) ON DELETE CASCADE,
    dados         JSONB       NOT NULL DEFAULT '{}',
    ausencias     JSONB       NOT NULL DEFAULT '{}',
    versao        INTEGER     NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE mensagem_processada (
    sessao_id        UUID        NOT NULL REFERENCES sessao_cadastro (id) ON DELETE CASCADE,
    mensagem_id      UUID        NOT NULL,
    versao_entrada   INTEGER     NOT NULL,
    versao_resultado INTEGER     NOT NULL,
    processada_em    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (sessao_id, mensagem_id)
);
