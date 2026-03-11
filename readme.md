## HA Media Cliente

### Usuario para descargar archivos de servidor linux local

1. Crear usuario dedicado con este comando
```bash
sudo adduser android
```
2. Crear las carpetas de almacenamiento
```bash
sudo mkdir -p /home/android/media 
sudo mkdir -p /home/android/docs 
sudo mkdir -p /home/android/data
```
3. Cambiar propietarios de las carpetas
```bash
sudo chown -R android:android /home/android/
```