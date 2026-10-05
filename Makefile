.PHONY: help build server run test clean add-image list-images

PORT ?= 8080
POWERSHELL = powershell -NoProfile -ExecutionPolicy Bypass -File

help:
	@echo make build  - Compila el nucleo Java
	@echo make server - Compila e inicia el servidor con imagenes.local.txt
	@echo make test   - Ejecuta las pruebas automatizadas
	@echo make clean  - Elimina solo los archivos compilados
	@echo make add-image ID=imagenNueva FILE="C:/ruta/imagen.png" - Registra una imagen
	@echo make list-images - Muestra las imagenes registradas
	@echo make server PORT=9090 - Usa otro puerto

build:
	$(POWERSHELL) ./scripts/Compilar.ps1

server:
	$(POWERSHELL) ./scripts/Iniciar.ps1 -Puerto $(PORT)

run: server

test:
	$(POWERSHELL) ./scripts/Probar.ps1

add-image:
	$(POWERSHELL) ./scripts/AgregarImagen.ps1 -Id "$(ID)" -Ruta "$(FILE)"

list-images:
	$(POWERSHELL) ./scripts/ListarImagenes.ps1

clean:
	$(POWERSHELL) ./scripts/Limpiar.ps1
