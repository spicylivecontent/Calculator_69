package com.calculator.app.ui.web

import com.calculator.app.network.AdBlockHosts
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Vad webbläsaren ska göra med en navigering. */
enum class NavigationDecision {
    /** Ladda i samma WebView. */
    ALLOW,

    /** Känd annons-/popunderdomän – ignorera helt. */
    BLOCK,

    /** Annan sajt eller app-schema – öppna utanför appen i stället. */
    EXTERNAL
}

/** Hur hård blockeringen ska vara. */
enum class AdBlockLevel(val id: String, val label: String) {
    STANDARD("standard", "Standard (rekommenderad)"),
    STRICT("strict", "Strikt – blockerar allt externt"),
    OFF("off", "Av");

    companion object {
        fun fromId(id: String?): AdBlockLevel = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * All reklamblockeringslogik för WebView-spelaren. Ren Kotlin (utom
 * [injectionScript] som bara bygger en sträng) så att reglerna kan enhetstestas.
 */
object WebAdBlock {

    private val mediaHosts = listOf(
        ".m3u8", ".mp4", ".webm", ".ts", ".mpd", ".vtt"
    )

    /** True om själva förfrågan aldrig får gå till nätet. */
    fun isBlockedRequest(url: String?, level: AdBlockLevel = AdBlockLevel.STANDARD, pageUrl: String? = null): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return true
        if (!parsed.isHttps) return true
        if (level == AdBlockLevel.OFF) return false
        if (level == AdBlockLevel.STRICT && pageUrl != null &&
            !sameSite(pageUrl.toHttpUrlOrNull()?.host.orEmpty(), parsed.host)) return true
        if (AdBlockHosts.isBlocked(parsed.host)) return true
        if (AdBlockHosts.looksLikeAdPath(parsed.encodedPath + "?" + parsed.encodedQuery)) return true
        return false
    }

    /**
     * Vad som ska hända när spelaren vill navigera. Samma sajt får laddas,
     * annonsdomäner blockeras och allt annat lämnar appen.
     */
    fun navigationFor(
        currentUrl: String?,
        targetUrl: String?,
        level: AdBlockLevel = AdBlockLevel.STANDARD
    ): NavigationDecision {
        val target = targetUrl ?: return NavigationDecision.BLOCK

        if (target.startsWith("http://", ignoreCase = true)) return NavigationDecision.BLOCK
        if (!target.startsWith("https://", ignoreCase = true)) {
            val scheme = target.substringBefore(':').lowercase(Locale.ROOT)
            return when {
                scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript" ->
                    NavigationDecision.BLOCK
                scheme in AdBlockHosts.externalSchemes -> NavigationDecision.BLOCK
                else -> NavigationDecision.EXTERNAL
            }
        }

        val parsedTarget = target.toHttpUrlOrNull() ?: return NavigationDecision.BLOCK
        val currentHost = currentUrl?.toHttpUrlOrNull()?.host.orEmpty().lowercase(Locale.ROOT)
        val targetHost = parsedTarget.host.lowercase(Locale.ROOT)

        // Disabling ad filtering must not disable the independent off-site navigation guard.
        if (level != AdBlockLevel.OFF && isBlockedRequest(target, level)) return NavigationDecision.BLOCK

        return when {
            sameSite(currentHost, targetHost) -> NavigationDecision.ALLOW
            level == AdBlockLevel.STRICT -> NavigationDecision.BLOCK
            isMedia(target) -> NavigationDecision.ALLOW
            else -> NavigationDecision.EXTERNAL
        }
    }

    /** Sant om båda hör till samma registrerbara domän (t.ex. xvideos.com och cdn.xvideos.com). */
    fun sameSite(currentHost: String, targetHost: String): Boolean {
        if (currentHost.isBlank() || targetHost.isBlank()) return false
        if (currentHost == targetHost) return true
        return registrable(currentHost) == registrable(targetHost)
    }

    fun isMedia(url: String): Boolean {
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.ROOT) ?: return false
        return mediaHosts.any { path.endsWith(it) }
    }

    private fun registrable(host: String): String =
        "https://$host".toHttpUrlOrNull()?.let { it.topPrivateDomain() ?: it.host }.orEmpty()

    /** CSS som gömmer de vanligaste annons- och popunder-elementen. */
    val hideAdsCss: String = """
        ins.adsbygoogle, ins[data-ad-client], ins[data-ad-slot],
        iframe[src*="doubleclick"], iframe[src*="googlesyndication"], iframe[src*="exoclick"],
        iframe[src*="juicyads"], iframe[src*="trafficjunky"], iframe[src*="adsterra"],
        iframe[src*="popads"], iframe[src*="propellerads"], iframe[src*="clickadu"],
        iframe[id*="ad-"], iframe[class*="ad-"], iframe[name*="google_ads"],
        a[href*="exoclick"], a[href*="juicyads"], a[href*="trafficjunky"], a[href*="adsterra"],
        a[href*="propellerads"], a[href*="popads"], a[href*="clickadu"], a[href*="tsyndicate"],
        [id*="popunder"], [class*="popunder"], [class*="pop-under"],
        [class*="ad-overlay"], [id*="ad-overlay"], [class*="adblock-modal"],
        [class*="push-notification"], [id*="push-notification"],
        .ad, .ads, .advert, .advertisement, .adsbygoogle, .banner-ad, .banner_ads,
        div[id^="ad-"], div[id^="ads-"], div[class^="ad-"], div[class^="ads-"],
        div[id$="-ad"], div[class$="-ad"], [data-ad], [data-ad-slot], [data-ad-client],
        [aria-label="Advertisement"]
        { display: none !important; visibility: hidden !important; pointer-events: none !important; }
    """.trimIndent()

    /**
     * CSS som gör att sajtens videospelare direkt fyller hela skärmen (100vw x 100vh)
     * medan resten av hemsidan (menyer, kommentarer, relaterade videor) döljs, och
     * tar bort sajtens knapp för att minimera helskärm samtidigt som alla andra
     * spelarinställningar (kvalitet, hastighet, ljud, tidslinje) behålls.
     */
    val fullscreenPlayerCss: String = """
        html, body {
            background: #000 !important;
            background-color: #000 !important;
            margin: 0 !important;
            padding: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            overflow: hidden !important;
            overscroll-behavior: none !important;
        }
        body[data-pc-locked="1"] *:not([data-pc-ancestor="1"]):not([data-pc-player="1"]):not([data-pc-player="1"] *) {
            visibility: hidden !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
        [data-pc-ancestor="1"] {
            visibility: visible !important;
            opacity: 1 !important;
            transform: none !important;
            -webkit-transform: none !important;
            filter: none !important;
            perspective: none !important;
            contain: none !important;
            clip-path: none !important;
            overflow: visible !important;
            position: static !important;
            z-index: 2147483646 !important;
            background: #000 !important;
            margin: 0 !important;
            padding: 0 !important;
        }
        [data-pc-player="1"] {
            position: fixed !important;
            inset: 0 !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            bottom: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            min-width: 100vw !important;
            min-height: 100vh !important;
            margin: 0 !important;
            padding: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            background: #000 !important;
            z-index: 2147483647 !important;
            visibility: visible !important;
            opacity: 1 !important;
            pointer-events: auto !important;
            transform: none !important;
            -webkit-transform: none !important;
        }
        [data-pc-video-wrap="1"] {
            width: 100% !important;
            height: 100% !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            padding: 0 !important;
            margin: 0 !important;
        }
        [data-pc-player="1"] video,
        video[data-pc-player="1"],
        iframe[data-pc-player="1"] {
            width: 100% !important;
            height: 100% !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            object-fit: contain !important;
            background: #000 !important;
            border: 0 !important;
        }
        video::-webkit-media-controls-fullscreen-button,
        .vjs-fullscreen-control,
        .jw-icon-fullscreen,
        [data-plyr="fullscreen"],
        .plyr__control--fullscreen,
        .fp-fullscreen,
        .fluid_button_fullscreen,
        .fluid_button_theatre,
        .mejs__fullscreen-button,
        .mejs-fullscreen-button,
        .mgp_fullscreen,
        .mhp1138_fullscreen,
        .xv-fullscreen,
        #xv-fullscreen,
        [class*="fullscreen-btn"],
        [class*="fullscreen_btn"],
        [class*="btn-fullscreen"],
        [class*="btn_fullscreen"],
        [class*="fullscreen-button"],
        [class*="fullscreen-toggle"],
        [class*="fullscreen-control"],
        [class*="icon-fullscreen"],
        [class*="fullscreen-icon"],
        button[title*="Fullscreen"],
        button[title*="fullscreen"],
        button[title*="Full screen"],
        button[title*="full screen"],
        button[title*="Helskärm"],
        button[title*="helskärm"],
        button[aria-label*="Fullscreen"],
        button[aria-label*="fullscreen"],
        button[aria-label*="Full screen"],
        button[aria-label*="full screen"],
        button[aria-label*="Helskärm"],
        button[aria-label*="helskärm"],
        [role="button"][title*="Fullscreen"],
        [role="button"][title*="fullscreen"],
        [role="button"][aria-label*="Fullscreen"],
        [role="button"][aria-label*="fullscreen"] {
            display:none!important;
            visibility: hidden !important;
            opacity: 0 !important;
            pointer-events: none !important;
            width: 0 !important;
            height: 0 !important;
            overflow: hidden !important;
        }
    """.trimIndent()

    /**
     * JavaScript som injiceras i varje sida: stänger fönsteröppningar, tar bort
     * annonsnoder, låser sajtens videospelare i helskärm utan att visa övriga
     * hemsidan, förhindrar att helskärm minimeras och exponerar hjälpfunktioner.
     */
    fun injectionScript(level: AdBlockLevel = AdBlockLevel.STANDARD): String = buildString {
        append("(function(){")
        append("if(!document.documentElement){return;}")
        append("if(document.documentElement.getAttribute('data-pc-init')==='1'){if(window.__pcFullscreen){window.__pcFullscreen();}return;}")
        append("document.documentElement.setAttribute('data-pc-init','1');")
        append("window.__pcInstalled=true;")
        append("var LEVEL='").append(level.id).append("';")
        // 1) Döda popups, popunders och försök att lämna helskärm på hemsidan.
        append("try{window.open=function(){return null;};")
        append("window.alert=function(){};")
        append("var noopFs=function(){return Promise.resolve();};")
        append("document.exitFullscreen=noopFs;document.webkitExitFullscreen=noopFs;document.webkitCancelFullScreen=noopFs;")
        append("if(window.Document&&Document.prototype){Document.prototype.exitFullscreen=noopFs;Document.prototype.webkitExitFullscreen=noopFs;Document.prototype.webkitCancelFullScreen=noopFs;}")
        append("if(window.HTMLVideoElement&&HTMLVideoElement.prototype){HTMLVideoElement.prototype.webkitExitFullscreen=noopFs;HTMLVideoElement.prototype.webkitExitFullScreen=noopFs;}")
        append("document.addEventListener('click',function(e){")
        append("var t=e.target;")
        append("var btn=t&&t.closest?t.closest('button,[role=\"button\"],a,div,span'):null;")
        append("if(btn){var meta=((btn.id||'')+' '+(typeof btn.className==='string'?btn.className:'')+' '+(btn.getAttribute('title')||'')+' '+(btn.getAttribute('aria-label')||'')+' '+(btn.getAttribute('data-plyr')||'')).toLowerCase();")
        append("if(/fullscreen|full-screen|full_screen|helskärm/.test(meta)&&!/quality|setting|speed|volume|mute/.test(meta)){e.preventDefault();e.stopPropagation();return false;}}")
        append("var a=t&&t.closest?t.closest('a[target=\"_blank\"]'):null;")
        append("if(a){a.removeAttribute('target');a.setAttribute('rel','noopener noreferrer');}")
        append("},true);}catch(e){}")
        if (level != AdBlockLevel.OFF) {
        // 2) Göm annonser med CSS och städa bort dem löpande.
        append("try{var css=").append(jsString(hideAdsCss)).append(";")
        append("var add=function(){var s=document.createElement('style');s.type='text/css';")
        append("s.appendChild(document.createTextNode(css));(document.head||document.documentElement).appendChild(s);};")
        append("add();")
        append("var sweep=function(){try{")
        append("var nodes=document.querySelectorAll('iframe,ins,[id*=\"popunder\"],[class*=\"popunder\"],a[href*=\"exoclick\"],a[href*=\"juicyads\"],a[href*=\"trafficjunky\"],a[href*=\"adsterra\"],a[href*=\"onclickads\"],[class*=\"ad-overlay\"]');")
        append("for(var i=0;i<nodes.length;i++){var n=nodes[i];if(!n||!n.parentNode){continue;}")
        append("var src=(n.getAttribute&&(n.getAttribute('src')||n.getAttribute('href'))||'').toLowerCase();")
        append("var idc=((n.id||'')+(n.className&&n.className.baseVal?n.className.baseVal:(n.className||''))).toLowerCase();")
        append("if(/exoclick|juicyads|trafficjunky|adsterra|popads|propellerads|clickadu|doubleclick|googlesyndication|onclickads|popunder|ad-overlay/.test(src+idc)){n.parentNode.removeChild(n);}")
        append("}}catch(e){}};")
        append("setTimeout(sweep,800);setTimeout(sweep,2500);setTimeout(sweep,6000);")
        append("setTimeout(add,1500);}catch(e){}")
        }
        // 3) Videohjälp: spolning + position + kontrollval.
        append("window.__pcVideo=function(){try{")
        append("var list=document.querySelectorAll('video');var best=null;")
        append("for(var i=0;i<list.length;i++){var v=list[i];var d=isFinite(v.duration)?v.duration:0;")
        append("if(!best||d>(isFinite(best.duration)?best.duration:0)){best=v;}}return best;}catch(e){return null;}};")
        append("window.__pcState=function(){try{var v=window.__pcVideo();if(!v){return '{}';}")
        append("return JSON.stringify({t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0,p:!!v.paused,vw:v.videoWidth||0,vh:v.videoHeight||0,m:!!v.muted});}catch(e){return '{}';}};")
        append("window.__pcSeekBy=function(s){try{var v=window.__pcVideo();if(!v){return 'no';}")
        append("var d=isFinite(v.duration)?v.duration:0;var t=(v.currentTime||0)+s;if(d>0){t=Math.max(0,Math.min(d-0.5,t));}")
        append("v.currentTime=Math.max(0,t);return 'ok';}catch(e){return 'err';}};")
        append("window.__pcToggle=function(){try{var v=window.__pcVideo();if(!v){return 'no';}")
        append("if(v.paused){v.play();}else{v.pause();}return 'ok';}catch(e){return 'err';}};")
        append("window.__pcControls=function(on){try{window.__pcUserControls=!!on;var v=window.__pcVideo();if(!v){return 'no';}")
        append("v.controls=!!on;if(on){v.setAttribute('controls','controls');v.setAttribute('controlsList','nofullscreen');}else{v.removeAttribute('controls');}return 'ok';}catch(e){return 'err';}};")
        append("window.__pcResume=function(seconds){try{window.__pcResumeAt=(seconds>15)?seconds:0;return 'ok';}catch(e){return 'err';}};")
        append("try{setInterval(function(){var v=window.__pcVideo();if(!v||!window.__pcResumeAt){return;}")
        append("var d=isFinite(v.duration)?v.duration:0;if(d>window.__pcResumeAt+20&&(v.currentTime||0)<5){v.currentTime=window.__pcResumeAt;window.__pcResumeAt=0;}},1200);}catch(e){}")
        // 4) Isolera sajtens videospelare direkt i helskärm utan överliggande dubblettkontroller.
        append("try{var fsCss=").append(jsString(fullscreenPlayerCss)).append(";")
        append("var ensureFsStyle=function(){if(document.getElementById('__pc_fs_style')){return;}")
        append("var s=document.createElement('style');s.id='__pc_fs_style';s.type='text/css';")
        append("s.appendChild(document.createTextNode(fsCss));(document.head||document.documentElement).appendChild(s);};")
        append("ensureFsStyle();")
        append("window.__pcPlayerRoot=function(v){if(!v){return null;}var best=v;var cur=v.parentElement;var depth=0;")
        append("while(cur&&cur!==document.body&&cur!==document.documentElement&&depth<8){")
        append("var tag=(cur.tagName||'').toLowerCase();if(tag==='main'||tag==='article'||tag==='body'||tag==='html'){break;}")
        append("if(cur.querySelector('header,nav,footer,form,h1,#comments,.comments,#related,.related-videos')){break;}")
        append("if(cur.querySelectorAll('a[href]').length>10){break;}")
        append("var idc=((cur.id||'')+' '+(typeof cur.className==='string'?cur.className:'')).toLowerCase();")
        append("if(/page|content-wrap|main-wrap|layout|site-/.test(idc)&&!/player|video/.test(idc)){break;}")
        append("best=cur;")
        append("if(/html5video|video-js|jwplayer|plyr|flowplayer|fp-player|mejs__container|mgp_container|xplayer|video-player|video_player|player-container|player_container|player-wrapper|video-box|video_box/.test(idc)){")
        append("var next=cur.parentElement;var nextIdc=next?((next.id||'')+' '+(typeof next.className==='string'?next.className:'')).toLowerCase():'';")
        append("if(!/player|video/.test(nextIdc)){break;}}")
        append("cur=cur.parentElement;depth++;}return best;};")
        append("window.__pcFullscreen=function(){try{ensureFsStyle();")
        append("var v=window.__pcVideo();")
        append("if(v){v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');v.setAttribute('controlsList','nofullscreen');}")
        append("var root=v?window.__pcPlayerRoot(v):document.querySelector('#html5video,#player,#video-player-bg,#video-player,#video_player,.video-player,.player-container,#player-container,.video-js,.jwplayer,.plyr,#video_box,#moviexxx,iframe[src*=\"player\"],iframe[src*=\"embed\"],iframe[src*=\"mydaddy\"],iframe[allowfullscreen]');")
        append("if(v&&root===v&&!v.hasAttribute('controls')){v.setAttribute('controls','controls');}")
        append("if(root){var wrapParent=v?v.parentElement:null;")
        append("if(window.__pcCurrentRoot!==root||window.__pcCurrentVideo!==v||window.__pcCurrentWrap!==wrapParent){")
        append("var old=document.querySelectorAll('[data-pc-player],[data-pc-ancestor],[data-pc-video-wrap]');")
        append("for(var i=0;i<old.length;i++){old[i].removeAttribute('data-pc-player');old[i].removeAttribute('data-pc-ancestor');old[i].removeAttribute('data-pc-video-wrap');}")
        append("root.setAttribute('data-pc-player','1');")
        append("if(v&&v!==root){var w=v.parentElement;while(w&&w!==root&&w!==document.body){w.setAttribute('data-pc-video-wrap','1');w=w.parentElement;}}")
        append("var a=root.parentElement;while(a&&a!==document.body&&a!==document.documentElement){a.setAttribute('data-pc-ancestor','1');a=a.parentElement;}")
        append("if(document.body){document.body.setAttribute('data-pc-locked','1');}")
        append("window.__pcCurrentRoot=root;window.__pcCurrentVideo=v;window.__pcCurrentWrap=wrapParent;")
        append("try{window.dispatchEvent(new Event('resize'));}catch(e){}")
        append("}else if(document.body&&!document.body.hasAttribute('data-pc-locked')){document.body.setAttribute('data-pc-locked','1');}}")
        append("if(v&&!window.__pcAutoStarted){")
        append("var pr=v.play();if(pr&&pr.then){pr.then(function(){window.__pcAutoStarted=true;}).catch(function(){});}else if(!v.paused){window.__pcAutoStarted=true;}")
        append("if(v.paused&&(v.currentTime||0)<0.2&&!window.__pcClickedPlay&&root&&root!==v){")
        append("var pb=root.querySelector('.vjs-big-play-button,.fp-playbtn,.fluid_initial_play,.mgp_bigPlay,#html5video_play,.jw-display-icon-display,[class*=\"big-play\"]');")
        append("if(pb){window.__pcClickedPlay=true;pb.click();}}}")
        append("}catch(e){}};")
        append("window.__pcFullscreen();")
        append("document.addEventListener('DOMContentLoaded',window.__pcFullscreen);")
        append("window.addEventListener('load',window.__pcFullscreen);")
        append("setTimeout(window.__pcFullscreen,300);setTimeout(window.__pcFullscreen,900);setTimeout(window.__pcFullscreen,2000);")
        append("setInterval(window.__pcFullscreen,800);}catch(e){}")
        append("})();")
    }

    /** Gör om en sträng till ett säkert JavaScript-strängliteral. */
    fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        return "\"$escaped\""
    }
}
