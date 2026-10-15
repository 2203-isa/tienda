package com.hh.limpia

/** Descripciones cortas en español de apps comunes en Brasil y Colombia. */
object AppDescriptions {

    private val MAP: Map<String, String> = mapOf(
        // Mensajería
        "com.whatsapp" to "WhatsApp: mensajes, audios, llamadas y videollamadas. Sus fotos y videos recibidos ocupan mucho espacio.",
        "com.whatsapp.w4b" to "WhatsApp Business: WhatsApp para negocios (catálogo, respuestas automáticas, etiquetas de clientes).",
        "org.telegram.messenger" to "Telegram: mensajería con grupos y canales grandes; guarda mucho contenido descargado.",
        "org.thoughtcrime.securesms" to "Signal: mensajería privada y cifrada.",
        "com.facebook.orca" to "Messenger: el chat de Facebook.",
        "com.facebook.mlite" to "Messenger Lite: versión liviana del chat de Facebook.",
        "com.discord" to "Discord: chat por voz y texto en comunidades (muy usado para juegos).",
        "com.viber.voip" to "Viber: mensajes y llamadas gratis por internet.",
        "jp.naver.line.android" to "LINE: mensajería popular en Asia.",
        "com.tencent.mm" to "WeChat: mensajería y pagos (China).",
        "com.imo.android.imoim" to "imo: llamadas y videollamadas gratis.",
        "com.skype.raider" to "Skype: llamadas y videollamadas (Microsoft).",
        "com.google.android.apps.messaging" to "Mensajes de Google: SMS y chat RCS.",
        "com.truecaller" to "Truecaller: identifica quién llama y bloquea spam.",

        // Redes sociales
        "com.instagram.android" to "Instagram: red social de fotos, reels e historias.",
        "com.instagram.barcelona" to "Threads: red social de textos cortos de Instagram.",
        "com.facebook.katana" to "Facebook: red social.",
        "com.facebook.lite" to "Facebook Lite: versión liviana de Facebook (gasta menos datos y espacio).",
        "com.facebook.pages.app" to "Meta Business Suite: administrar páginas de Facebook e Instagram de un negocio.",
        "com.zhiliaoapp.musically" to "TikTok: videos cortos. Guarda mucha caché (videos vistos).",
        "com.zhiliaoapp.musically.go" to "TikTok Lite: versión liviana de TikTok.",
        "com.ss.android.ugc.trill" to "TikTok (versión internacional).",
        "com.kwai.video" to "Kwai: videos cortos (parecido a TikTok).",
        "com.kwai.bulldog" to "Kwai: videos cortos (parecido a TikTok).",
        "com.snapchat.android" to "Snapchat: fotos y videos que desaparecen, filtros.",
        "com.pinterest" to "Pinterest: ideas e imágenes de inspiración.",
        "com.twitter.android" to "X (Twitter): red social de noticias y opiniones.",
        "com.reddit.frontpage" to "Reddit: foros de comunidades.",
        "com.linkedin.android" to "LinkedIn: red social de trabajo y empleos.",
        "com.tinder" to "Tinder: app de citas.",
        "com.bumble.app" to "Bumble: app de citas y amistad.",
        "com.badoo.mobile" to "Badoo: app de citas.",
        "tv.twitch.android.app" to "Twitch: transmisiones en vivo (juegos, charlas).",

        // Video y música
        "com.google.android.youtube" to "YouTube: videos.",
        "com.google.android.apps.youtube.music" to "YouTube Music: música.",
        "com.spotify.music" to "Spotify: música y podcasts. Las canciones descargadas ocupan espacio.",
        "deezer.android.app" to "Deezer: música.",
        "com.soundcloud.android" to "SoundCloud: música de artistas independientes.",
        "com.shazam.android" to "Shazam: reconoce qué canción está sonando.",
        "com.netflix.mediaclient" to "Netflix: películas y series. Lo descargado ocupa mucho espacio.",
        "com.amazon.avod.thirdpartyclient" to "Prime Video: películas y series de Amazon.",
        "com.disney.disneyplus" to "Disney+: películas y series.",
        "com.wbd.stream" to "Max (HBO): películas y series.",
        "com.hbo.hbonow" to "HBO: películas y series.",
        "com.globo.globotv" to "Globoplay: novelas, series y TV Globo.",
        "tv.pluto.android" to "Pluto TV: canales de TV gratis.",
        "com.crunchyroll.crunchyroid" to "Crunchyroll: anime.",
        "org.videolan.vlc" to "VLC: reproductor de videos y música.",
        "com.mxtech.videoplayer.ad" to "MX Player: reproductor de videos.",
        "com.snaptube.premium" to "SnapTube: descargar videos de internet (ocupa espacio con las descargas).",
        "com.nemo.vidmate" to "VidMate: descargar videos de internet.",
        "com.audible.application" to "Audible: audiolibros.",
        "wp.wattpad" to "Wattpad: leer y escribir historias.",
        "com.google.android.apps.books" to "Google Play Libros: libros y audiolibros.",

        // IA
        "com.openai.chatgpt" to "ChatGPT: asistente de inteligencia artificial (OpenAI).",
        "com.google.android.apps.bard" to "Gemini: asistente de inteligencia artificial de Google.",
        "com.anthropic.claude" to "Claude: asistente de inteligencia artificial (Anthropic).",
        "com.microsoft.copilot" to "Copilot: asistente de inteligencia artificial de Microsoft.",
        "com.deepseek.chat" to "DeepSeek: asistente de inteligencia artificial.",
        "ai.perplexity.app.android" to "Perplexity: buscador con inteligencia artificial.",
        "ai.x.grok" to "Grok: asistente de inteligencia artificial de X.",
        "ai.character.app" to "Character.AI: chatear con personajes de IA.",
        "com.google.android.apps.googleassistant" to "Asistente de Google.",
        "com.amazon.dee.app" to "Alexa: asistente de Amazon y casa inteligente.",

        // Bancos y pagos
        "com.nu.production" to "Nubank: banco digital (cuenta, tarjeta, Pix).",
        "com.picpay" to "PicPay: billetera digital y Pix.",
        "br.com.intermedium" to "Banco Inter: banco digital.",
        "com.itau" to "Itaú: banco.",
        "br.com.bradesco" to "Bradesco: banco.",
        "com.santander.app" to "Santander: banco.",
        "br.com.gabba.Caixa" to "Caixa: banco.",
        "br.gov.caixa.tem" to "Caixa Tem: cuenta digital de Caixa (beneficios del gobierno).",
        "br.com.bb.android" to "Banco do Brasil: banco.",
        "com.c6bank.app" to "C6 Bank: banco digital.",
        "br.com.uol.ps.myaccount" to "PagBank: banco digital de PagSeguro.",
        "com.mercadopago.wallet" to "Mercado Pago: billetera digital, Pix y pagos.",
        "com.todo1.mobile" to "Bancolombia: banco.",
        "com.nequi.MobileApp" to "Nequi: billetera digital de Colombia.",
        "com.davivienda.daviplataapp" to "DaviPlata: billetera digital de Colombia.",
        "com.paypal.android.p2pmobile" to "PayPal: pagos internacionales.",
        "com.binance.dev" to "Binance: criptomonedas.",
        "com.transferwise.android" to "Wise: enviar dinero a otros países.",
        "com.remitly.androidapp" to "Remitly: enviar dinero a otros países.",
        "com.google.android.apps.walletnfcrel" to "Google Wallet: pagar con el celular.",
        "com.samsung.android.spay" to "Samsung Wallet: pagar con el celular.",
        "br.com.serasaexperian.consumidor" to "Serasa: consultar CPF, score y deudas.",

        // Gobierno Brasil
        "br.gov.meugovbr" to "gov.br: acceso a servicios del gobierno de Brasil.",
        "br.gov.caixa.fgts.trabalhador" to "FGTS: consultar el FGTS.",
        "br.gov.dataprev.meuinss" to "Meu INSS: servicios del INSS.",
        "br.gov.serpro.cnhe" to "Carteira Digital de Trânsito: CNH digital.",

        // Compras y comida
        "com.mercadolibre" to "Mercado Libre / Mercado Livre: compras por internet.",
        "com.shopee.br" to "Shopee: compras por internet.",
        "com.einnovation.temu" to "Temu: compras baratas por internet.",
        "com.zzkko" to "SHEIN: ropa por internet.",
        "com.alibaba.aliexpresshd" to "AliExpress: compras de China.",
        "com.amazon.mShop.android.shopping" to "Amazon: compras por internet.",
        "com.luizalabs.mlapp" to "Magalu: compras por internet.",
        "com.schibsted.bomnegocio.androidApp" to "OLX: comprar y vender usados.",
        "br.com.dafiti" to "Dafiti: ropa y zapatos.",
        "com.netshoes.app" to "Netshoes: ropa y zapatos deportivos.",
        "com.nike.omega" to "Nike: tienda Nike.",
        "com.adidas.app" to "adidas: tienda adidas.",
        "com.inditex.zara" to "Zara: tienda de ropa.",
        "br.com.brainweb.ifood" to "iFood: pedir comida a domicilio.",
        "com.grability.rappi" to "Rappi: domicilios de comida y mercado.",
        "com.pedidosya" to "PedidosYa: domicilios de comida.",
        "com.ubercab.eats" to "Uber Eats: domicilios de comida.",

        // Transporte y viajes
        "com.ubercab" to "Uber: pedir carro.",
        "com.taxis99" to "99: pedir carro o moto.",
        "com.didiglobal.passenger" to "DiDi: pedir carro.",
        "sinet.startup.inDriver" to "inDrive: pedir carro negociando el precio.",
        "com.cabify.rider" to "Cabify: pedir carro.",
        "com.google.android.apps.maps" to "Google Maps: mapas y rutas.",
        "com.waze" to "Waze: rutas y tráfico en tiempo real.",
        "com.booking" to "Booking: reservar hoteles.",
        "com.airbnb.android" to "Airbnb: alquilar casas y cuartos para viajes.",

        // Fotos, edición y nube
        "com.google.android.apps.photos" to "Google Fotos: galería y copia de seguridad de fotos en la nube.",
        "com.google.android.apps.docs" to "Google Drive: guardar archivos en la nube.",
        "com.microsoft.skydrive" to "OneDrive: nube de Microsoft (la usa la Galería de Samsung).",
        "com.dropbox.android" to "Dropbox: guardar archivos en la nube.",
        "mega.privacy.android.app" to "MEGA: nube con mucho espacio gratis.",
        "com.box.android" to "Box: nube para archivos.",
        "com.google.android.apps.subscriptions.red" to "Google One: administrar el almacenamiento de tu cuenta Google.",
        "com.lemon.lvoverseas" to "CapCut: editar videos. Guarda borradores que ocupan mucho espacio.",
        "com.camerasideas.instashot" to "InShot: editar videos y fotos.",
        "com.canva.editor" to "Canva: diseñar posts, flyers y presentaciones.",
        "com.picsart.studio" to "Picsart: editar fotos.",
        "com.adobe.lrmobile" to "Lightroom: editar fotos.",
        "com.vsco.cam" to "VSCO: filtros y edición de fotos.",
        "com.google.ar.lens" to "Google Lens: buscar con la cámara.",
        "com.intsig.camscanner" to "CamScanner: escanear documentos con la cámara.",
        "com.adobe.scan.android" to "Adobe Scan: escanear documentos.",
        "com.microsoft.office.officelens" to "Microsoft Lens: escanear documentos.",

        // Trabajo y estudio
        "com.microsoft.office.outlook" to "Outlook: correo.",
        "com.google.android.gm" to "Gmail: correo.",
        "com.yahoo.mobile.client.android.mail" to "Yahoo Mail: correo.",
        "com.microsoft.teams" to "Teams: reuniones y chat de trabajo.",
        "us.zoom.videomeetings" to "Zoom: videollamadas y reuniones.",
        "com.google.android.apps.tachyon" to "Google Meet: videollamadas.",
        "com.microsoft.office.officehubrow" to "Microsoft 365 (Office): Word, Excel y PowerPoint.",
        "cn.wps.moffice_eng" to "WPS Office: abrir y editar documentos.",
        "com.adobe.reader" to "Adobe Acrobat: abrir PDF.",
        "com.google.android.apps.classroom" to "Google Classroom: clases y tareas.",
        "com.duolingo" to "Duolingo: aprender idiomas.",
        "com.google.android.apps.translate" to "Google Traductor.",
        "com.google.android.keep" to "Google Keep: notas.",
        "com.google.android.calendar" to "Google Calendar: agenda.",
        "com.google.android.apps.tasks" to "Google Tasks: lista de tareas.",
        "com.notion.id" to "Notion: notas y organización.",
        "com.evernote" to "Evernote: notas.",
        "com.trello" to "Trello: organizar tareas en tableros.",
        "com.todoist" to "Todoist: lista de tareas.",
        "com.github.android" to "GitHub: proyectos de programación.",
        "com.google.android.apps.authenticator2" to "Google Authenticator: códigos de verificación (¡no la borres si la usas para entrar a tus cuentas!).",
        "com.azure.authenticator" to "Microsoft Authenticator: códigos de verificación (¡no la borres si la usas!).",

        // Navegadores y utilidades
        "com.android.chrome" to "Chrome: navegador de internet.",
        "com.sec.android.app.sbrowser" to "Samsung Internet: navegador de internet.",
        "org.mozilla.firefox" to "Firefox: navegador de internet.",
        "com.opera.browser" to "Opera: navegador de internet.",
        "com.opera.mini.native" to "Opera Mini: navegador que ahorra datos.",
        "com.brave.browser" to "Brave: navegador que bloquea anuncios.",
        "com.microsoft.emmx" to "Edge: navegador de Microsoft.",
        "com.google.android.apps.nbu.files" to "Files de Google: administrador de archivos y limpieza.",
        "com.rarlab.rar" to "RAR: abrir archivos comprimidos.",
        "com.lenovo.anyshare.gps" to "SHAREit: pasar archivos entre celulares (tiene mucha publicidad).",
        "com.cleanmaster.mguard" to "Clean Master: limpiador (no es necesario, tiene mucha publicidad).",
        "com.avast.android.mobilesecurity" to "Avast: antivirus.",
        "com.kms.free" to "Kaspersky: antivirus.",
        "com.samsung.android.oneconnect" to "SmartThings: controlar aparatos inteligentes de la casa.",
        "com.xiaomi.smarthome" to "Mi Home: aparatos inteligentes Xiaomi.",
        "com.google.android.apps.fitness" to "Google Fit: actividad física.",
        "com.strava" to "Strava: registrar carreras y ciclismo.",

        // Juegos
        "com.roblox.client" to "Roblox: juego (ocupa bastante espacio).",
        "com.dts.freefireth" to "Free Fire: juego de disparos (ocupa mucho espacio).",
        "com.dts.freefiremax" to "Free Fire MAX: juego de disparos (ocupa mucho espacio).",
        "com.supercell.clashofclans" to "Clash of Clans: juego de estrategia.",
        "com.supercell.clashroyale" to "Clash Royale: juego de cartas y estrategia.",
        "com.supercell.brawlstars" to "Brawl Stars: juego de acción.",
        "com.king.candycrushsaga" to "Candy Crush: juego de dulces.",
        "com.mojang.minecraftpe" to "Minecraft: juego de construir.",
        "com.activision.callofduty.shooter" to "Call of Duty Mobile: juego de disparos (ocupa mucho espacio).",
        "com.tencent.ig" to "PUBG Mobile: juego de disparos (ocupa mucho espacio).",
        "com.ea.gp.fifamobile" to "EA FC Mobile: juego de fútbol.",
        "com.kiloo.subwaysurf" to "Subway Surfers: juego de correr.",
        "com.miHoYo.GenshinImpact" to "Genshin Impact: juego de aventura (ocupa muchísimo espacio).",
        "com.innersloth.spacemafia" to "Among Us: juego en grupo.",
    )

    fun builtIn(pkg: String): String? = MAP[pkg]

    fun guess(pkg: String, category: String): String? {
        val p = pkg.lowercase()
        return when {
            p.contains("bank") || p.contains("banco") || p.contains("wallet") -> "Parece una app de banco o pagos."
            p.contains("game") || p.contains("games") || category.contains("Juego", true) -> "Parece un juego."
            p.contains("vpn") -> "VPN: cambia o protege tu conexión a internet."
            p.contains("cleaner") || p.contains("booster") -> "Limpiador / acelerador (normalmente no hace falta)."
            p.contains("launcher") -> "Cambia la pantalla de inicio del celular."
            p.contains("keyboard") || p.contains("teclado") -> "Teclado."
            p.contains("camera") -> "Cámara o editor de fotos."
            else -> null
        }
    }
}
