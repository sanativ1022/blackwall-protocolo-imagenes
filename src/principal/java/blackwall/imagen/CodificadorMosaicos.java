package blackwall.imagen;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class CodificadorMosaicos {
    public byte[] previa(BufferedImage imagen) throws IOException {
        BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graficos = rgb.createGraphics();
        try { graficos.drawImage(imagen, 0, 0, null); } finally { graficos.dispose(); }
        var escritores = ImageIO.getImageWritersByFormatName("jpeg");
        if (!escritores.hasNext()) throw new IOException("No hay codificador JPEG");
        var escritor = escritores.next();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream(); var flujo = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            var parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.55f);
            escritor.write(null, new IIOImage(rgb, null, null), parametros);
            flujo.flush(); return salida.toByteArray();
        } finally { escritor.dispose(); }
    }
    public byte[] finalSinPerdida(BufferedImage imagen) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (!ImageIO.write(imagen, "png", salida)) throw new IOException("No hay codificador PNG");
            return salida.toByteArray();
        }
    }
}
