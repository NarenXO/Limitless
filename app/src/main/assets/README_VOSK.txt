VOSK MODEL SETUP INSTRUCTIONS
=============================

The Limitless app uses Vosk for offline speech-to-text captions. To enable live captions,
you need to place the Vosk model in the assets directory.

REQUIRED MODEL:
- vosk-model-small-en-us-0.15 (or compatible small English model)

PLACEMENT:
1. Download the model from: https://alphacephei.com/vosk/models
   - Direct link: https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
2. Extract the downloaded archive
3. Copy the extracted folder to: app/src/main/assets/vosk-model-small-en-us/
   (The folder should contain subdirectories like am/, conf/, graph/, etc.)

DIRECTORY STRUCTURE:
app/src/main/assets/
└── vosk-model-small-en-us/
    ├── am/
    │   └── mfcc.conf
    ├── conf/
    │   └── ...
    ├── graph/
    │   └── ...
    └── ...

IMPORTANT:
- The model is approximately 50MB in size
- This model provides English language recognition
- The app will automatically extract the model from assets to internal storage on first run
- If the model is missing, the app will show "Caption model not loaded" error chip
- For other languages, download the appropriate model from Vosk and update the
  MODEL_PATH constant in VoskCaptionEngine.kt

NOTE FOR DEVELOPERS:
The model is not included in the repository due to its size. You must download and place
it manually before testing the live captions feature.
