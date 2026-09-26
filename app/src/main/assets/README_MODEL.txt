===================================================================
HAZEL ASSISTANT - WAKE WORD DETECTION MODEL INSTRUCTIONS
===================================================================

This directory contains the TensorFlow Lite model used for on-device 
"Hey Hazel" wake-word detection.

CURRENT STATE:
`hey_hazel.tflite` is currently a placeholder file. The WakeWordListener
detects if the model is a valid TFLite file and will gracefully fall back 
to no-op mode without crashing if the placeholder is present.

HOW TO REPLACE WITH TRAINED MODEL:
1. Train an audio keyword spotter model on Google Teachable Machine 
   (Audio Project) or TensorFlow for the keyword "Hey Hazel".
2. Export the trained model in TensorFlow Lite (.tflite) format.
3. Replace `app/src/main/assets/hey_hazel.tflite` with your exported 
   .tflite file.
4. Ensure the output classes include "Hey Hazel" at index 1 (or 
   adjust threshold settings in DefaultWakeWordListener if needed).
5. Build and run the app. Zero code changes are required!
===================================================================
