CREATE TABLE pessoa_cadastro (
    id                     UUID PRIMARY KEY,
    nome                   VARCHAR(200) NOT NULL,
    nome_social            VARCHAR(200),
    nascimento             DATE,
    situacao_cpf           VARCHAR(20)  NOT NULL,
    cpf                    CHAR(11),
    forma_contato          VARCHAR(20)  NOT NULL,
    telefone               VARCHAR(20),
    email                  VARCHAR(254),
    referencia_nome        VARCHAR(200),
    referencia_vinculo     VARCHAR(100),
    referencia_canal       VARCHAR(254),
    municipio              VARCHAR(120),
    uf                     VARCHAR(2),
    endereco_fixo          BOOLEAN,
    logradouro             VARCHAR(200),
    numero                 VARCHAR(20),
    complemento            VARCHAR(100),
    bairro                 VARCHAR(120),
    cep                    VARCHAR(8),
    referencia_localizacao VARCHAR(300),
    ausencias              JSONB        NOT NULL DEFAULT '{}',
    criado_em              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pessoa_nome_preenchido CHECK (length(btrim(nome)) > 0),
    CONSTRAINT pessoa_situacao_cpf_valida CHECK (situacao_cpf IN (
        'INFORMADO', 'NAO_POSSUI', 'DESCONHECIDO', 'NAO_APRESENTADO')),
    CONSTRAINT pessoa_cpf_formato CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT pessoa_cpf_conforme_situacao CHECK ((situacao_cpf = 'INFORMADO') = (cpf IS NOT NULL)),
    CONSTRAINT pessoa_forma_contato_valida CHECK (forma_contato IN (
        'TELEFONE', 'EMAIL', 'REFERENCIA', 'INDISPONIVEL'))
);

-- CPF ausente fica NULL; a unicidade vale só para os valores presentes.
CREATE UNIQUE INDEX pessoa_cadastro_cpf_unico ON pessoa_cadastro (cpf) WHERE cpf IS NOT NULL;

CREATE TABLE representacao_declarada (
    pessoa_id           UUID PRIMARY KEY REFERENCES pessoa_cadastro (id),
    nome_quem_preenche  VARCHAR(200) NOT NULL,
    vinculo_declarado   VARCHAR(100) NOT NULL,
    declarada_em        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE declaracao_socioeconomica (
    id                 UUID PRIMARY KEY,
    pessoa_id          UUID        NOT NULL REFERENCES pessoa_cadastro (id),
    sessao_origem_id   UUID        NOT NULL REFERENCES sessao_cadastro (id),
    competencia        DATE        NOT NULL,
    ocupacao           VARCHAR(120),
    renda_declarada    NUMERIC(15, 2),
    quantidade_membros INTEGER,
    dependentes        INTEGER,
    situacao_moradia   VARCHAR(20),
    ausencias          JSONB       NOT NULL DEFAULT '{}',
    coletada_em        TIMESTAMPTZ NOT NULL,
    CONSTRAINT declaracao_renda_nao_negativa CHECK (renda_declarada >= 0),
    CONSTRAINT declaracao_ao_menos_um_membro CHECK (quantidade_membros >= 1),
    CONSTRAINT declaracao_dependentes_nao_negativos CHECK (dependentes >= 0),
    CONSTRAINT declaracao_dependentes_cabem_no_nucleo CHECK (dependentes < quantidade_membros),
    CONSTRAINT declaracao_moradia_valida CHECK (situacao_moradia IN (
        'PROPRIA', 'ALUGADA', 'CEDIDA', 'ACOLHIMENTO', 'SEM_ENDERECO_FIXO', 'OUTRA'))
);

CREATE INDEX declaracao_socioeconomica_pessoa_idx ON declaracao_socioeconomica (pessoa_id);

CREATE TABLE confirmacao_cadastro (
    id                 UUID PRIMARY KEY,
    sessao_id          UUID        NOT NULL UNIQUE REFERENCES sessao_cadastro (id),
    versao_revisada    INTEGER     NOT NULL,
    chave_idempotencia UUID        NOT NULL UNIQUE,
    cadastro_id        UUID        NOT NULL REFERENCES pessoa_cadastro (id),
    com_pendencias     BOOLEAN     NOT NULL,
    confirmado_em      TIMESTAMPTZ NOT NULL
);
