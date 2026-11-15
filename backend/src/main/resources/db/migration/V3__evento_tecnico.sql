-- Só metadados técnicos: nada de chave, CPF ou conteúdo da conversa.
CREATE TABLE evento_tecnico (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(40) NOT NULL,
    status         VARCHAR(40) NOT NULL,
    modo           VARCHAR(20),
    modelo         VARCHAR(100),
    duracao_ms     BIGINT,
    correlacao     UUID        NOT NULL,
    ocorrido_em    TIMESTAMPTZ NOT NULL
);

CREATE INDEX evento_tecnico_ocorrido_em_idx ON evento_tecnico (ocorrido_em);
