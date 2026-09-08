# LocalVPN - Android Ad Blocker & DNS Filter

A powerful, lightweight Android VPN application that blocks ads, trackers, and unwanted content at the DNS level. Built with Kotlin, Jetpack Compose, and Android's native VPN APIs.

## How It Works

### Core Architecture

**1. Local VPN Service (`AdBlockVpnService.kt`)**
- Creates a local VPN tunnel using Android's `VpnService` API
- Intercepts all DNS queries (UDP port 53) from the device
- Routes traffic through a virtual interface (10.0.0.2/32)
- Processes packets in a dedicated thread with coroutine-based handling

**2. DNS Interception & Filtering**
- Parses IP and UDP headers from intercepted packets
- Extracts DNS query information
- Checks queried domains against blocklists
- Returns `0.0.0.0` for blocked domains (preventing connection)
- Forwards allowed queries to real DNS servers (8.8.8.8, 1.1.1.1)

**3. Blocklist Management (`BlockListManager.kt`)**
Uses a three-tier blocking system:
- **Hardcoded defaults**: Major trackers (Google Analytics, Facebook Pixel, etc.)
- **Asset-based blocklist**: Large database loaded from `ad-websites.txt`
- **User-defined domains**: Custom blocklist stored in Room database
- **Keyword detection**: Blocks domains containing tracking-related keywords

**4. Domain Trie Structure (`DomainTrie.kt`)**
- Efficient prefix tree for O(m) domain matching (m = domain length)
- Supports wildcard matching for subdomains
- Thread-safe with volatile references

**5. Quick Settings Tile (`VpnTileService.kt`)**
- System-integrated toggle in Android's quick settings panel
- One-tap VPN activation/deactivation
- Visual state feedback (active/inactive)

### Data Flow

```
App Request → DNS Query → VPN Interface → Packet Parser → Domain Check
                                              ↓
                                    Blocked → Return 0.0.0.0
                                    Allowed → Forward to DNS Server
                                              ↓
                                    Return Response to App
```

## Project Structure

```
com.elbaroudi.localvpn/
├── AdBlockVpnService.kt      # Main VPN service (packet interception)
├── BlockListManager.kt         # Blocklist loading & domain checking
├── VpnTileService.kt           # Quick settings tile
├── MainActivity.kt             # Entry point with navigation
├── NetworkPacket.kt            # IP/UDP packet parsing
├── DnsForwarder.kt             # DNS query forwarding
├── ByteBufferPool.kt           # Memory-efficient buffer management
├── data/                       # Room database & repositories
│   ├── AppDatabase.kt
│   ├── BlockedDomain.kt
│   └── BlockedDomainDao.kt
├── dns/                        # DNS packet parsing
│   └── DnsPacket.kt
├── ui/                         # Jetpack Compose screens
│   ├── HomeScreen.kt           # Main VPN toggle screen
│   ├── BlockListScreen.kt      # Domain management
│   ├── VpnViewModel.kt         # State management
│   └── theme/                  # Material3 theming
└── utils/
    └── DomainTrie.kt           # Efficient domain matching
```

## Current Features

### Existing Strengths

- **Fast Quick Tile Access**: Toggle VPN instantly from Android's quick settings panel without opening the app
- **Bilingual Support**: Full Arabic and English localization with in-app language switching
- **Persistent Notification**: Always-visible notification with stop action when VPN is active
- **Dual Blocklist System**: Hardcoded trackers + user-customizable lists
- **Modern UI**: Built with Jetpack Compose and Material3 design
- **Efficient Memory Management**: ByteBuffer pooling for packet processing
- **App Self-Exclusion**: Prevents infinite loops by excluding itself from VPN routing
- **Keyword-Based Blocking**: Catches tracking domains via pattern matching

### Technical Highlights

- **Coroutine-Based Processing**: Non-blocking packet handling with structured concurrency
- **Thread-Safe Writes**: Synchronized TUN interface writes prevent race conditions
- **Efficient Domain Matching**: Trie-based O(m) lookup for massive blocklists
- **Room Database**: Persistent storage for user-defined blocked domains
- **Edge-to-Edge UI**: Modern Android UI with full screen utilization

## Recommended Future Features

### High Priority Improvements

**1. Real VPN Server Connectivity**
- Support for WireGuard/OpenVPN protocols
- Connect to external VPN servers while maintaining ad-blocking
- Split tunneling: route only specific apps through VPN
- Multiple server locations with latency testing

**2. Modern & Clean UI Redesign**
- Dashboard with real-time statistics (blocked requests, data saved)
- Animated connection states
- Material You dynamic theming
- Smooth transitions and micro-interactions
- Dark/Light/System theme support

**3. Per-App Blocking & Control**
- App whitelist/blacklist for VPN routing
- Block specific apps from network access entirely
- App-specific DNS settings
- View which apps are making blocked requests

### Medium Priority Features

**4. Advanced Blocking Categories**
- Toggle categories: Ads, Trackers, Malware, Adult Content, Gambling, Social Media
- Custom category creation
- Schedule-based blocking (e.g., block social media during work hours)
- Location-based rules

**5. Statistics & Analytics**
- Daily/weekly/monthly blocking reports
- Most blocked domains chart
- Data usage savings calculation
- Real-time request monitor

**6. Cloud Sync & Backup**
- Sync blocklists across devices
- Backup/restore user settings
- Community-curated blocklist subscriptions

### Enhanced Functionality

**7. DNS Over HTTPS/TLS (DoH/DoT)**
- Encrypt DNS queries for privacy
- Custom DNS provider selection (Cloudflare, Quad9, NextDNS)
- Fallback DNS configuration

**8. Firewall Features**
- IP-based blocking
- Port filtering
- Protocol blocking (UDP/TCP control)
- Geo-blocking by country

**9. Battery & Performance Optimization**
- Adaptive blocking (reduce checks during low battery)
- Sleep mode with reduced activity
- Memory usage optimization for low-end devices

**10. Additional Utilities**
- Network speed test
- Ping monitoring
- Data usage tracker per app
- Network log export

## Installation & Setup

### Requirements
- Android 7.0+ (API 24+)
- VPN permission approval on first launch

### Build Instructions

```bash
# Clone the repository
git clone <repository-url>
cd localvpn

# Build debug APK
./gradlew assembleDebug

# Or install directly to connected device
./gradlew installDebug
```

### First Run
1. Install the APK
2. Open the app
3. Tap "Start" button
4. Grant VPN permission when prompted
5. (Optional) Add custom domains to blocklist
6. (Optional) Add Quick Settings tile for instant access

## Technical Details

### VPN Configuration
- **Local Address**: 10.0.0.2/32
- **DNS Server**: 10.0.0.1 (virtual)
- **Route**: 10.0.0.1/32 (DNS-only interception)
- **MTU**: Default system MTU

### Blocklist Sources
The app can load blocklists from:
- Hardcoded tracker domains (always active)
- Assets file (`ad-websites.txt`)
- User-defined domains (Room database)
- Keyword pattern matching

### Dependencies
- **Jetpack Compose**: Modern declarative UI
- **Room**: Local database persistence
- **Kotlin Coroutines**: Asynchronous processing
- **Material3**: Latest Material Design components
- **Navigation Compose**: In-app navigation

## Architecture Decisions

**Why Local VPN Instead of Root?**
- Works on all devices without root access
- System-level ad blocking across all apps
- Safer and more compatible approach

**Why DNS-Level Blocking?**
- Low battery impact compared to proxy-based solutions
- Fast domain-level decision making
- Minimal performance overhead

**Why Trie Structure for Domains?**
- O(m) lookup time where m is domain length
- Memory efficient for massive blocklists
- Supports wildcard subdomain matching

## Contributing

Contributions are welcome! Areas for contribution:
- Additional blocklist sources
- UI/UX improvements
- VPN protocol integrations
- Performance optimizations
- Translations

## License


## Contact & Support

- Instagram: [@abdohigh](https://instagram.com/abdohigh)
- Issues: [GitHub Issues](https://github.com/yourusername/localvpn/issues)

---

**Note**: This app uses Android's VPN APIs for local traffic filtering. No data is sent to external servers; all processing happens locally on your device.
