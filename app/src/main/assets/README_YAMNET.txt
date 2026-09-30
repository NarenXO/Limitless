YAMNet Sound Classification Model
===================================

This directory contains the YAMNet TFLite model for environmental sound classification.

FILES:
- yamnet_placeholder.tflite: Placeholder file (not the actual model)
- yamnet_label_list.txt: Standard YAMNet audio class labels

HOW TO GET THE ACTUAL MODEL:
1. Download YAMNet TFLite model from:
   https://huggingface.co/STMicroelectronics/yamnet/resolve/main/yamnet_e256_64x96_tl_int8.tflite
2. Rename the downloaded file to "yamnet.tflite"
3. Place it in app/src/main/assets/
4. Update SoundClassifier.kt to use "yamnet.tflite" instead of "yamnet_placeholder.tflite"

SUPPORTED SOUND CLASSES:
- Siren
- Dog bark
- Cat meow
- Frying
- Pop
- Water tap
- Alarm
- Music
- Doorbell
- Knock
- Cough
- Baby cry
- Car horn
- Engine
- Drill
- Hammer
- Saw
- Speech
