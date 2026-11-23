# MEMORI AI — Product Vision

## Non-negotiable product rules
- No subscriptions, paid tiers, ads, or paywalls.
- No API key or provider secret embedded in the Android APK or source repository.
- Prefer on-device, open-source AI runtimes and downloadable model files.
- Do not claim unlimited capability: disclose device RAM, storage, battery, thermal, and model-size constraints.
- Network-dependent features (such as live web search) must be optional and clearly identified; never silently introduce a paid service.
- Request Android permissions only when a user invokes the related feature, and require confirmation for sensitive actions.

## Product goal
Build MEMORI AI as a practical Android digital assistant, not only a chat screen. The target experience includes text and voice conversation, long-term user-controlled memory, multilingual interaction, image/document understanding, task assistance, and consent-based Android integrations. A realistic animated 3D avatar is a later presentation layer, not a substitute for a working assistant engine.

## Architecture direction
1. Android app shell: conversation UI, accessibility-conscious controls, character selection, settings, permission prompts, local history, and explicit privacy controls.
2. On-device inference: evaluate a compatible open-source Android runtime and quantized models. Model choice must be based on actual device capability and measured latency; do not bundle a large model blindly into the APK.
3. Local data: store user-approved memory and conversation history on-device, with clear view, edit, export, and delete controls.
4. Speech: use available on-device speech recognition and text-to-speech where supported; show when a device language pack or system service is missing.
5. Optional online tools: only add free/open endpoints when their current terms and limits are understood. No paid endpoint is required for core local chat.
6. Android actions: implement each integration separately, using official Android APIs and runtime permission/confirmation flows. Never promise unrestricted control of the phone.

## Delivery stages
- Stage 0: preserve a buildable Android baseline and document current gaps.
- Stage 1: add a real local inference integration and model download/import flow; test on the target phone before selecting a default model.
- Stage 2: add persistent, user-controlled local memory and conversation history.
- Stage 3: improve voice interaction with explicit listening/speaking states and interruption handling.
- Stage 4: add file/image understanding and selected Android integrations.
- Stage 5: upgrade the avatar to a real-time 3D character after core assistant functions are stable.

## Current baseline (as of this document)
The existing app has a Java Android shell, a drawn 2D animated face, Android SpeechRecognizer input, Android TextToSpeech output, character selection, and an optional HTTPS chat endpoint setting. It does not yet contain an on-device LLM runtime, persistent semantic memory, image/document understanding, a real 3D avatar, or broad Android task integrations. A successful APK build is not proof that these features are complete.

## Acceptance principles
- Every release must build successfully.
- Test core flows on a real Android device, not only in CI.
- Verify offline chat after the model is installed and airplane mode is enabled.
- Verify that no paid account, subscription, or payment method is requested for core features.
- Publish model license, model size, minimum RAM guidance, and known limitations before offering a model download.
