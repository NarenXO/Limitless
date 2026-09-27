YAMNet Sound Classification Model Setup
========================================

This directory must contain the following files for sound alert functionality:

1. yamnet.tflite
   - TensorFlow Lite model for environmental sound classification
   - Download from: https://tfhub.dev/google/yamnet/1
   - Convert to TFLite using TensorFlow Lite converter if needed
   - Place directly in this assets folder

2. yamnet_label_list.txt
   - Text file containing the 521 AudioSet class labels
   - One label per line, matching the model's output indices
   - Download from YAMNet model repository or generate from model metadata
   - Place directly in this assets folder

Installation Steps:
-------------------
1. Download yamnet.tflite model file
2. Download or generate the label list file
3. Place both files in: app/src/main/assets/
4. Rebuild the app

Model Information:
------------------
- Input: 0.975 seconds of audio (16kHz, mono)
- Output: 521 class probabilities (AudioSet labels)
- Target sounds detected: Siren, Fire Alarm, Doorbell
- Inference: On-device, offline-capable

Fallback Behavior:
------------------
If model files are missing, the app will:
- Display a "Model missing" warning chip in the UI
- Not crash, but sound alerts will be disabled
- Allow manual testing of vibration patterns