# Beluga Android: Threat Model & Security Specification

This document details the threat model, cryptographic security guarantees, adversary assumptions, and defense implementations for Beluga Android v2.

---

## Threat Model & Adversary Assumptions

Beluga Android assumes an adversarial environment characterized by completely untrusted transport media, aggressive eavesdropping, and physical proximity threats:

### 1. Untrusted Optical & Image Transport
- **Threat**: The communication channel consists entirely of physical display screens, printed codes, camera viewfinders, and exported PNG images. Adversaries may observe, photograph, intercept, replicate, or alter these QR codes in transit.
- **Defense**: All payload data is encrypted with AES-256-GCM and authenticated via context-bound Additional Authenticated Data (AAD). Optical eavesdroppers learn nothing about message contents or sender identities beyond public session identifiers.

### 2. Active Man-in-the-Middle (MITM) & Impersonation
- **Threat**: An active adversary intercepts an ephemeral handshake QR code and substitutes their own ephemeral key, attempting to terminate separate sessions with Alice and Bob.
- **Defense**: Mode 3 handshake packets require an ECDSA signature over the canonical handshake transcript including both participants' long-term identity public keys, the ephemeral public key, and a timestamp. An attacker cannot forge this signature without possessing the victim's long-term private key.

### 3. Replay & Reflection Attacks
- **Threat**: An adversary records a previously transmitted message or handshake QR code and displays it back to the sender (reflection) or presents an old message again at a later time (replay).
- **Defense**:
  - **Directional Session Keys**: Distinct transmission and reception root keys (`txKey != rxKey`) derived from deterministically sorted endpoints ensure an endpoint will fail to decrypt a reflected packet.
  - **Strict Monotonic Sequence Counter**: The receiver tracks `lastRxSeq` and rejects any packet with $\text{seq} \le \text{lastRxSeq}$ with a `ReplayAttackException`.
  - **Handshake Timestamp Windows**: Handshake packets are rejected if the timestamp deviates by more than +/- 300 seconds from local clock time.

### 4. Physical Device Seizure & Data-at-Rest Extraction
- **Threat**: An attacker obtains physical access to a locked or powered-off Android device and attempts to extract keys or messages from internal flash storage.
- **Defense**: The local vault (`database.bin`) is encrypted using AES-256-GCM with a 256-bit key derived from the user's master password via 300,000 iterations of PBKDF2-HMAC-SHA256 and HKDF-SHA256. Without the master password, the vault is cryptographically indistinguishable from random bytes.

### 5. Screen Capture, Video Recording & Shoulder Surfing
- **Threat**: Malicious foreground apps, screen recording software, or shoulder surfers capture displayed QR codes or plaintext UI.
- **Defense**:
  - `WindowManager.LayoutParams.FLAG_SECURE` is active throughout the entire application lifecycle, blocking OS screenshots, screencasting, and system app-switcher thumbnails.
  - An inactivity auto-lock timer locks the vault and purges keys from memory after 2 minutes of backgrounding.

---

## Cryptographic Guarantees

| Security Property | Cryptographic Mechanism | Implementation Detail |
| :--- | :--- | :--- |
| **Confidentiality** | AES-256-GCM | 256-bit key, unique 12-byte random IV per message |
| **Integrity & Authenticity** | GCM 128-bit Tag + AAD | Cryptographically binds `v2\|<sid>\|<seq>` to ciphertext |
| **Forward Secrecy** | Ephemeral ECDH over P-384 | Fresh ephemeral key pairs per session |
| **Identity Authentication** | ECDSA P-384 Signatures | `SHA256withECDSA` over canonical Mode 3 transcript |
| **Out-of-Band Verification**| 60-digit Safety Numbers | HKDF-SHA256 expansion into 12 decimal groups of 5 |
| **Replay Protection** | Monotonic Sequence Numbers | Strictly increasing sequence counters enforced in session state |
| **Vault Protection** | PBKDF2-HMAC-SHA256 (300k) | Hardware-resistant brute-force mitigation + HKDF |
| **Memory Sanitization** | Key Zeroization & Auto-Lock | Ephemeral keys overwritten and cleared on lock |

---

## Vulnerability Remediation Matrix (v1 -> v2)

The original prototype (`beluga-e2ee` v1) suffered from 22 critical architectural and cryptographic vulnerabilities. The table below documents each issue and its concrete resolution in `beluga-android` v2:

| # | Vulnerability / Flaw in v1 | Root Cause in v1 | Resolution in Beluga Android v2 |
| :-: | :--- | :--- | :--- |
| **1** | **zlib Adler-32 trailing 0x00 hang** | Fixed-size buffer zero-padding caused zlib inflater to enter an infinite loop waiting for end-of-stream. | Strict dynamic buffer trimming and explicit EOF termination in `zlibCompress` / `zlibDecompress`. |
| **2** | **Zip Bomb Denial of Service** | Unbounded zlib inflation allowed malicious compressed payloads to exhaust device memory. | Enforced strict `MAX_DECOMPRESSED_BYTES` ceiling (64 KB) in `zlibDecompress` with immediate abort. |
| **3** | **Symmetric Reflection / Replay** | Identical session key used for both send and receive allowed reflection of ciphertexts. | Directional session keys (`txKey != rxKey`) derived via deterministic endpoint key sorting and HKDF domain separation. |
| **4** | **Unauthenticated Handshake MITM** | Handshake packets contained bare ephemeral keys without cryptographic proof of identity. | Mode 3 transcript signing with ECDSA P-384 binding signer ID, both identity keys, ephemeral key, and timestamp. |
| **5** | **Cross-Contact Handshake Contamination** | Global singleton pending handshake state allowed concurrent handshakes to overwrite each other. | Scoped pending handshakes per `userId` in `DatabaseRepository` and `E2eSystem`. |
| **6** | **Missing AEAD Associated Data (AAD)** | AES-GCM lacked AAD, enabling ciphertext splicing and session substitution attacks. | Bound `v2\|<sid>\|<seq>` as authenticated additional data on all messages and `v2\|beluga-db` on vault files. |
| **7** | **Invalid Curve / Small Subgroup Attacks** | Imported public keys were parsed without verifying curve membership or point at infinity. | Enforced SEC 1 point validation, length checks (49 bytes), and curve membership verification in `bytesToPublicKey`. |
| **8** | **Multi-Chunk Reassembly Drop & Hang** | Multi-frame QR codes dropped out-of-order packets and froze UI during scanning. | Added `PendingChunkState` with unordered chunk buffering, deduplication, and real-time scanning progress indicators. |
| **9** | **Plaintext Leakage via Screenshots & OS** | Android OS captured recent app switcher thumbnails containing decrypted plaintext and QR codes. | Enabled `WindowManager.LayoutParams.FLAG_SECURE` across all application windows and secure cache file sharing. |
| **10** | **Camera Permission Crash** | Missing runtime permission checks caused unhandled security exceptions on startup. | Integrated CameraX runtime permission lifecycle handler with in-place educational rationale and recovery. |
| **11** | **Backstack Memory Retention** | Pressing the back button after vault locking exposed previously cached session activities. | Automatic navigation redirect to `Unlock` screen upon lock with complete backstack purging. |
| **12** | **Unattended Memory Exposure** | Sensitive keys remained in memory indefinitely when the app was backgrounded. | Implemented `ProcessLifecycleOwner` auto-lock timer triggering after 2 minutes of app backgrounding. |
| **13** | **Ungraceful Exit Database Corruption** | Direct writes to `database.bin` corrupted the JSON database if interrupted mid-write. | Atomic two-phase write mechanism: write to `.tmp`, `fsync()`, copy to `.bak`, and atomic rename to `database.bin`. |
| **14** | **Keystore Desynchronization Lockout** | Reliance on Android Keystore master keys caused unrecoverable vault lockouts across device backups. | Self-contained, portable encrypted vault using standard PBKDF2-HMAC-SHA256 (300k iterations) and HKDF. |
| **15** | **Concurrent Access Race Conditions** | Multi-threaded file operations caused intermittent file-locking and state synchronization errors. | Guarded all repository mutations and disk I/O with Kotlin coroutine `Mutex` synchronization. |
| **16** | **Handshake Invalidation on Screen Rotation** | Activity recreation generated new ephemeral keys, invalidating QR codes already shown to peers. | Idempotent `initiateOrResumeHandshake` persisting ongoing handshakes in the database across UI recreations. |
| **17** | **First-Run Password Typo Lockout** | Initial vault creation did not require password confirmation, risking permanent lockout. | Mandatory password confirmation field and client-side equality validation in `UnlockViewModel`. |
| **18** | **ML Kit Memory Leaks & Bloat** | Heavy closed-source ML Kit dependencies bloated APK and leaked CameraX image proxies. | 100% open-source ZXing engine with direct Y-plane byte array luminance extraction and zero GC pressure. |
| **19** | **Key Retention in Garbage Collector** | Private key bytes remained in memory indefinitely after session closure. | Explicit zeroization and memory dereferencing upon database locking and session closure. |
| **20** | **Dark Mode QR Contrast Degradation** | Dark themes inverted QR code colors, rendering codes unreadable by external optical scanners. | Forced high-contrast white card container with ISO 18004-compliant quiet zones on all QR displays. |
| **21** | **Lack of Human Identity Verification** | Users could not verify peer identity keys out-of-band to prevent active MITM attacks. | Signal-style 60-digit human-verifiable safety numbers displayed in 12 groups of 5 decimal digits. |
| **22** | **Global Security Provider Collision** | Calling `Security.insertProviderAt()` mutated the global JVM security configuration, causing clashes. | Isolated BouncyCastle provider singleton passed explicitly to JCA cipher and key factory factories. |
