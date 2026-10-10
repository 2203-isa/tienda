# H.H | M.F. Store

Tienda virtual de H.H | M.F. Store (Hernán Hernández y Malu França): catálogo de tenis, carrito con pedido por WhatsApp, asistentes con voz y tres idiomas (español, portugués e inglés).

Se puede instalar como app en celular, tablet y computador desde el navegador.

- `index.html`: la tienda completa (fotos y voces incluidas). El catálogo se actualiza desde Supabase: lo que se agrega o cambia en el panel administrador (conectado con correo y clave) lo ven todos los clientes; si no hay Internet, se muestra la última versión guardada.
- `contabilidad.html`: la contabilidad de la tienda (ventas, inventario, proveedores, facturas, pagos, gastos, trabajadores, impuestos, inversión, caja y reportes). Se entra desde el panel administrador con correo y clave; los datos se guardan en Supabase y solo los ven los correos autorizados.
- `intro.mp4`, `intro.webm`, `intro.jpg`: la entrada de la tienda (video del logo con música original). Se muestra al abrir la tienda, como máximo cada 6 horas; el botón «Entrar a la tienda» la inicia con sonido y «Saltar» la omite.
- `manifest.webmanifest`, `sw.js` e íconos: lo que permite instalarla como app y abrirla sin conexión.
