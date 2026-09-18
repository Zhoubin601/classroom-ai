import onnxruntime as ort

print("onnxruntime version:", ort.__version__)

print("providers:")
print(ort.get_available_providers())

print("device:")
print(ort.get_device())