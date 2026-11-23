# MatchStats — escopo do MVP

App Android nativo em Kotlin, Jetpack Compose e Material 3, com interface em português do Brasil e funcionamento offline.

| Área | Escopo |
| --- | --- |
| Início | Totais reais de equipes, jogadores e partidas, partidas recentes e atalhos para fluxos disponíveis. |
| Equipes | Criar, visualizar, editar e excluir equipes. Escolher futebol ou basquete e consultar o elenco. |
| Jogadores | Adicionar, editar e excluir jogadores. Vincular o mesmo jogador a várias equipes, com número da camisa, posição e indicação de capitão por elenco. |
| Partidas | Criar, visualizar, editar e excluir partidas. Selecionar equipe, adversário, data e esporte. Suportar partidas agendadas e finalizadas e registrar o placar final. |
| Futebol | Gols, assistências, cartões amarelos e cartões vermelhos. |
| Basquete | Pontos, rebotes e assistências. |
| Estatísticas | Calcular totais a partir das partidas registradas, montar rankings básicos de jogadores e resumir o desempenho das equipes. |
| Perfil | Perfil local simples e informações básicas do app. |

Fora do MVP ficam login, cadastro de contas, sincronização em nuvem, múltiplos usuários, acompanhamento ao vivo, notificações push, animações avançadas, análises complexas e APIs esportivas externas.

## Modelo Room

- **Equipe** — ID, nome, modalidade, categoria e treinador opcionais e data de criação.
- **Jogador** — ID, nome e data de criação. Pode existir sem equipe.
- **Vínculo de elenco** — chave composta por jogador e equipe, número de camisa e posição opcionais e indicação de capitão. O número informado é único por equipe. A exclusão de um vínculo preserva o jogador e suas estatísticas.
- **Partida** — ID, equipe (chave estrangeira), nome do adversário, data e hora, local opcional, status, placar e data de criação. A modalidade é derivada da equipe e o placar só existe em partidas finalizadas.
- **Estatística de jogador** — chave composta por partida e jogador, com campos de contagem para as métricas da modalidade. Cada linha pertence a um jogador e a uma partida acompanhada ao vivo ou finalizada. A equipe histórica vem da partida, independentemente do elenco atual. Totais e rankings são derivados dessas linhas.
- **Perfil local** — nome opcional salvo nas preferências do aparelho, com acesso à versão e às informações do app.

- **Sessão ao vivo** — uma por partida, com placar, etapa e posição dos cronômetros. O histórico registra jogador da casa ou camisa do adversário, ação, métrica, lado e correções.

Valide a modalidade e os participantes ao gravar partidas e defina transações para alterações que afetem registros vinculados. Preserve o histórico ao definir exclusões que afetem partidas.

## Fase 1

Estrutura Gradle, tema, componentes, navegação e estados iniciais das cinco abas. Os formulários, as telas de detalhe, o banco e os cálculos entram nas próximas fases. Os filtros e a busca guardam o estado da interface, e os estados vazios refletem a ausência de registros.

## Fase 2

Room v1 persiste equipes e jogadores. A camisa aceita números inteiros de 0 a 99. A alteração da modalidade de uma equipe limpa as posições do elenco na mesma transação. O Início observa os totais de equipes e jogadores.

## Fase 3

Room v2 adiciona partidas e estatísticas individuais por partida. A migração mantém as equipes e os jogadores. Uma partida finalizada permanece nos resultados. Equipes com partidas mantêm a modalidade. Jogadores e partidas com estatísticas registradas mantêm o vínculo com a equipe. As exclusões confirmadas removem estatísticas vinculadas em cascata.

## Fase 4

O painel calcula desempenho e rankings de futebol e basquete com dados de partidas finalizadas. O filtro usa todas as equipes da modalidade ou uma equipe específica. A taxa de vitórias divide vitórias pelo total de jogos finalizados. Empates já registrados no basquete são exibidos quando existem. A soma de gols individuais só pode ser salva dentro do placar da equipe. Um placar editado também deve cobrir os gols registrados. Dados anteriores são mantidos para correção manual.

## Expansão aprovada

### Fase 5 · Cadastro global e reutilização de jogadores

O Room v3 preserva IDs, equipes, partidas e estatísticas do v2. O diretório lista jogadores com ou sem equipe e permite reutilizar o mesmo cadastro em vários elencos. O perfil reúne vínculos atuais, histórico e estatísticas de jogos por modalidade. Jogos registrados contam partidas finalizadas com uma linha de estatísticas individuais.

### Fase 6 · Box scores configuráveis

O Room v4 adiciona a configuração padrão por modalidade, a configuração por partida, o catálogo personalizado e as medições complementares. As novas partidas copiam o padrão ao serem criadas. Partidas anteriores começam com o catálogo padrão da modalidade. Ocultar colunas só altera a exibição. Arquivar uma categoria a retira dos padrões e mantém o ID, as medições e as colunas das partidas que já a usavam.

Os dois editores de jogo usam a mesma linha canônica para gols, assistências, cartões, pontos e rebotes. As demais medições ficam vinculadas a essa linha por ID estável de métrica. O aproveitamento soma acertos e tentativas registrados, inclusive nos totais da equipe, e exibe um traço quando não há tentativas. Rebotes ofensivos e defensivos são registros independentes do total histórico. Eventos ao vivo e edições manuais usam esses mesmos totais.

### Fase 7 · Registro ao vivo

O Room v5 adiciona sessões, histórico e preferências de ações. Uma partida figura como agendada até a finalização gravar o placar oficial. O basquete usa tempo regressivo, posse com reset configurado ou de 14 segundos e faltas por período. O futebol mostra o tempo transcorrido em dois tempos. Os cronômetros usam tempo monotônico no processo atual, gravam checkpoints nas ações e a cada 15 segundos na tela ao vivo, e voltam pausados no último checkpoint quando outro processo abre a partida.

Cada evento tem um ID estável e aplica a diferença aos campos canônicos e às medições existentes na mesma transação que o placar. Desfazer ou corrigir grava uma nova entrada no histórico e reverte somente a contribuição vigente. Uma finalização no alvo também conta como finalização. Cada rebote por subtipo incrementa o total uma vez. A edição manual fica bloqueada durante a sessão. Após finalizar, o box score aceita ajustes manuais compatíveis com as contribuições do histórico. Reduções dessas contribuições e ajustes do placar passam pelo histórico auditado. Descartar uma sessão em andamento reverte seus lançamentos e preserva a base anterior.

As ações têm ordem e visibilidade próprias, independentes das colunas do box score. Métricas personalizadas de total podem ser incrementadas ao vivo. O adversário usa camisas e nomes opcionais no escopo da partida. Seu box score é projetado a partir dos eventos vigentes e fica separado dos jogadores globais.

### Fase 8 · Práticas e estatísticas em lote

O Room v6 adiciona sessões, participantes globais, exercícios e lotes em uma migração aditiva. O número de camisa é copiado do vínculo com o elenco para identificar o participante na prática. Cada lote tem um ID estável e pode salvar vários jogadores em uma transação. A correção altera a contribuição vigente da linha, preserva os valores originais e mantém o histórico separado dos jogos.

A aba Partidas oferece acesso à agenda e aos resultados de práticas. O lançamento aceita contagem ou acertos e tentativas por jogador, com rascunhos por exercício e salvamento individual ou coletivo. O cronômetro opcional usa tempo monotônico e volta pausado no último checkpoint após reinício.

O perfil e o painel de estatísticas têm contextos de jogos e práticas. Práticas concluídas contam a participação dos jogadores selecionados e os exercícios com lançamentos. O aproveitamento divide a soma dos acertos pela soma das tentativas. O Início mostra o total de práticas e a próxima agendada.

### Fase 9 · Quadro tático de basquete

O quadro usa Canvas nativo para meia quadra ou quadra inteira. Marcadores de ataque e defesa, bola e traços usam coordenadas normalizadas. Movimentos, passes e bloqueios podem ser apagados individualmente. O editor desfaz e refaz alterações da sessão e oferece apresentação com a quadra protegida contra gestos de edição.

O Room v7 acrescenta jogadas com equipe opcional, modo da quadra, datas e um documento JSON versionado. A associação dos marcadores usa IDs do elenco sem alterar seus registros. A exclusão de uma equipe mantém a jogada e remove o vínculo com a equipe. O diretório fica acessível pelo Início e pelos detalhes de equipes de basquete.

Toda a expansão segue o tema original do MatchStats.
