# SoundSmart - 2 Zone Matrix (MZ21)

Driver Hubitat para o **SoundSmart SS-MZ21**, amplificador matricial com 3 módulos LinkPlay independentes (Zona Master + Zona 1 + Zona 2).

---

## Sobre o hardware

O SS-MZ21 possui **3 zonas de áudio independentes**, cada uma com seu próprio módulo LinkPlay e endereço IP na rede:

| Zona | Amplificação | Entradas disponíveis |
|---|---|---|
| **Master** | Não (saída de linha) | HDMI ARC, Óptico, Streaming (WiFi), Bluetooth |
| **Zona 1** | Sim | HDMI, Óptico, Bluetooth, Line In (segue Master) |
| **Zona 2** | Sim | HDMI, Óptico, Bluetooth, Line In (segue Master) |

**Line In** nas Zonas 1 e 2 significa que elas seguem o áudio da Master — útil para distribuir a mesma fonte para todo o ambiente.

---

## Pré-requisitos

- Hubitat Elevation hub
- Rede local com os 3 IPs do MZ21 acessíveis ao hub
- Drivers instalados (os 3 são instalados juntos pelo HPM):
  - `SoundSmart - Player`
  - `SoundSmart MZ21` (Parent)
  - `SoundSmart MZ21 - Zona` (Child)

---

## Instalação via HPM

1. No Hubitat, abrir **Apps → Hubitat Package Manager**
2. Selecionar **Install**
3. Buscar por `SoundSmart MZ21` ou `2 Zone Matrix`
4. Instalar o pacote **SoundSmart - 2 Zone Matrix (MZ21)**
5. Os 3 drivers serão instalados automaticamente

---

## Configuração

### 1. Criar o dispositivo pai

1. Ir em **Devices → Add Device → Virtual**
2. Selecionar o driver `SoundSmart MZ21`
3. Dar um nome ao dispositivo (ex: `MZ21 - Sala`)
4. Salvar

### 2. Configurar os IPs

Na página do dispositivo pai, em **Preferences**:

| Campo | Descrição |
|---|---|
| `IP da Zona Master` | IP do módulo Master do MZ21 |
| `IP da Zona 1` | IP do módulo da Zona 1 |
| `IP da Zona 2` | IP do módulo da Zona 2 |
| `Intervalo de atualização` | 5s / 10s / 30s / 1min |
| `Ativar debug logging` | Liga logs detalhados (desliga sozinho em 30 min) |

> Os IPs precisam ser **estáticos** ou ter reserva DHCP no roteador. O driver faz polling HTTP periódico para cada zona.

Após preencher, clicar em **Save Preferences**.

### 3. Criar os dispositivos filho

Ainda na página do dispositivo pai, executar o comando:

```
createZoneDevices
```

Isso cria automaticamente **3 dispositivos filho** com os IPs já configurados:
- `MZ21 - Master Zone`
- `MZ21 - Zona 1`
- `MZ21 - Zona 2`

Cada filho aparece em **Devices** e pode ser usado individualmente em automações, dashboards e HomeKit.

> Para remover os filhos: executar o comando `removeZoneDevices` no dispositivo pai.

---

## Dispositivo Pai — Comandos e Atributos

O dispositivo pai exibe o status agregado das 3 zonas e centraliza os comandos de grupo.

### Atributos de status

| Atributo | Valores possíveis |
|---|---|
| `commStatus` | `good` / `error` / `unknown` |
| `masterInput` | `HDMI`, `Óptico`, `Bluetooth`, `Streaming`, `Line In`, `Radio Online`, `Spotify`, `Airplay` ... |
| `masterStatus` | `playing` / `paused` / `stopped` / `loading` |
| `masterVolume` | 0 – 100 |
| `masterTrack` | Título \| Artista (quando disponível) |
| `zone1Input` | (mesmo que master) |
| `zone1Status` | (mesmo que master) |
| `zone1Volume` | 0 – 100 |
| `zone1Track` | Título \| Artista |
| `zone1Muted` | `muted` / `unmuted` |
| `zone2Input` | (mesmo que zone1) |
| `zone2Status` | (mesmo que zone1) |
| `zone2Volume` | 0 – 100 |
| `zone2Track` | Título \| Artista |
| `zone2Muted` | `muted` / `unmuted` |

### Comandos — Master

| Comando | Descrição |
|---|---|
| `masterSetHDMI` | Troca Master para HDMI ARC |
| `masterSetOptical` | Troca Master para Óptico |
| `masterSetStreaming` | Troca Master para Streaming (WiFi) |
| `masterSetBluetooth` | Troca Master para Bluetooth |

### Comandos — Zona 1

| Comando | Descrição |
|---|---|
| `zone1FollowMaster` | Zona 1 passa para Line In (segue o áudio da Master) |
| `zone1Independent` | Zona 1 volta para Streaming independente |
| `zone1SetHDMI` | Zona 1 → HDMI |
| `zone1SetOptical` | Zona 1 → Óptico |
| `zone1SetBluetooth` | Zona 1 → Bluetooth |
| `zone1SetVolume` | Define volume (0–100) |
| `zone1VolumeUp` / `zone1VolumeDown` | Ajusta volume ±5 |
| `zone1Mute` / `zone1Unmute` | Muta / desmuta |
| `zone1Play` / `zone1Pause` / `zone1Stop` | Controle de reprodução |
| `zone1NextTrack` / `zone1PrevTrack` | Navega faixas |

### Comandos — Zona 2

Mesmos comandos com prefixo `zone2`.

### Comandos de grupo

| Comando | Descrição |
|---|---|
| `allZonesFollowMaster` | Zona 1 e Zona 2 passam para Line In (seguem Master) |
| `allZonesIndependent` | Zona 1 e Zona 2 voltam para Streaming independente |
| `allZonesStop` | Stop em todas as zonas |
| `allZonesMute` | Muta todas as zonas |
| `allZonesUnmute` | Desmuta todas as zonas |

### Gerenciamento dos filhos

| Comando | Descrição |
|---|---|
| `createZoneDevices` | Cria (ou atualiza) os 3 dispositivos filho |
| `removeZoneDevices` | Remove todos os filhos |

---

## Dispositivo Filho (por zona) — Comandos e Atributos

Cada filho (`MZ21 - Master Zone`, `MZ21 - Zona 1`, `MZ21 - Zona 2`) é um player completo baseado no driver `SoundSmart - Player`.

### Capabilities

`AudioVolume`, `MusicPlayer`, `Switch`, `SpeechSynthesis`, `PushableButton`, `Configuration`, `Initialize`, `Refresh`

### Atributos

| Atributo | Descrição |
|---|---|
| `commStatus` | `good` / `error` / `unknown` |
| `input` | Input atual da zona |
| `volume` | Volume atual (0–100) |
| `status` | `playing` / `paused` / `stopped` / `loading` |
| `trackDescription` | Nome da faixa/rádio (com arte de capa no Spotify) |
| `trackname` | Nome da faixa (texto simples) |
| `ipAddress` | IP configurado da zona |
| `URLLargeCoverFile` | URL da capa do álbum (Spotify) |
| `ImageLargeCover` | Imagem HTML da capa (Spotify) |
| `numberOfButtons` | Número de botões disponíveis (30) |

### Comandos de input

| Comando | Descrição |
|---|---|
| `inputwifi` | WiFi / Streaming |
| `inputoptical` | Óptico |
| `inputhdmi` | HDMI |
| `inputlinein` | Line In — zona passa a seguir o áudio da Master |

### Reprodução

| Comando | Descrição |
|---|---|
| `play` / `resume` | Play / Resume |
| `pause` | Pausa |
| `stop` | Para |
| `nextTrack` | Próxima faixa |
| `previousTrack` | Faixa anterior |
| `playTrack(url)` | Reproduz URL de áudio diretamente |
| `playTuneInStation(id)` | Toca rádio TuneIn pelo ID (ex: `s24940`) |
| `setVolume(0-100)` | Define volume |
| `volumeUp` / `volumeDown` | ±5 no volume |
| `mute` / `unmute` | Silenciar / dessilenciar |

### Presets e botões (push)

O driver suporta **30 botões** via capability `PushableButton`:

| Botão | Ação |
|---|---|
| 1 | WiFi / Streaming |
| 2 | Óptico |
| 4 | Line In (segue Master) |
| 6 | HDMI |
| 20–29 | Presets 1–10 (MCU) |
| 35 | Desativar prompts de voz |
| 36 | Ativar prompts de voz |
| 37 | Loop: Repeat All |
| 38 | Loop: Repeat Single |
| 39 | Loop: Shuffle Repeat |
| 40 | Loop: Shuffle No Repeat |

### Child switches (botões filho)

O comando `recreateChilds` cria dispositivos **Generic Component Switch** para cada ação, facilitando automações no Hubitat e integração com HomeKit/Alexa.

Também são criados automaticamente:
- **Volume Dimmer** (`Generic Component Dimmer`) — controla o volume como dimmer, compatível com HomeKit
- **Mute Toggle** (`Generic Component Switch`) — liga/desliga mute

Para remover: `removeChilds`.

### TTS (Text-to-Speech)

O driver implementa `SpeechSynthesis`. Basta usar o comando `speak(texto)` em automações — o Hubitat converte o texto para áudio via seu serviço de TTS padrão.

---

## Modos de input — referência

| Código (mode) | Input exibido |
|---|---|
| 0 | Sem Input |
| 1 | Airplay |
| 10 | Radio Online |
| 31 | Spotify |
| 40 | Line In (Segue Master) |
| 43 | Óptico |
| 49 | HDMI |

---

## Troubleshooting

**`commStatus` = error**
- Verificar se os IPs estão corretos e acessíveis pelo hub
- Testar no browser: `http://<IP_DA_ZONA>/httpapi.asp?command=getPlayerStatus`
- Verificar se o IP mudou (configurar reserva DHCP)

**Filhos não criados / driver não encontrado**
- Confirmar que os 3 drivers foram instalados pelo HPM antes de executar `createZoneDevices`

**Status não atualiza**
- O polling padrão é a cada 10 segundos. Alterar em Preferences se necessário.
- Executar `Refresh` manualmente para forçar atualização imediata.

**Debug**
- Ativar `debug logging` em Preferences (desliga automaticamente após 30 minutos).

---

## Arquitetura de dispositivos

```
SoundSmart MZ21 (Pai)
├── MZ21 - Master Zone   ← SoundSmart MZ21 - Zona  [IP_master]
├── MZ21 - Zona 1        ← SoundSmart MZ21 - Zona  [IP_zone1]
│   ├── MZ21-Zona - Volume (HomeKit)   ← Generic Component Dimmer
│   ├── MZ21-Zona - Mute/Unmute        ← Generic Component Switch
│   └── MZ21 - Input WiFi/Streaming, MZ21 - Preset 1..10 ... (child switches)
└── MZ21 - Zona 2        ← SoundSmart MZ21 - Zona  [IP_zone2]
    ├── MZ21-Zona - Volume (HomeKit)
    ├── MZ21-Zona - Mute/Unmute
    └── (child switches)
```

---

## Versões

| Versão | Data | Notas |
|---|---|---|
| 1.0.0 | 2026-06-24 | Versão inicial |

---

## Licença

Copyright 2026 VH / TRATO — Apache License 2.0
