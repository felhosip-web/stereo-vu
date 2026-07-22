import { useState, useEffect, useRef } from 'react';
import { Volume2, Play, Pause, Smartphone, Sliders, Shield, Zap, Info, Layers, RefreshCw, X, Terminal } from 'lucide-react';

export default function App() {
  const [isPlaying, setIsPlaying] = useState(false);
  const [decaySpeed, setDecaySpeed] = useState(0.88); // matches Kotlin code!
  const [peakHoldTime, setPeakHoldTime] = useState(900); // matches Kotlin code!
  const [gain, setGain] = useState(3.0); // matches Kotlin's rms*3 calibration!
  
  // Levels
  const [levelL, setLevelL] = useState(0);
  const [levelR, setLevelR] = useState(0);
  const [peakL, setPeakL] = useState(0);
  const [peakR, setPeakR] = useState(0);
  
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const sourceRef = useRef<MediaElementAudioSourceNode | null>(null);
  const audioElRef = useRef<HTMLAudioElement | null>(null);
  const peakHoldLRef = useRef<number>(0);
  const peakHoldRRef = useRef<number>(0);

  // Generate synthetic audio wave if no actual file played, for cool simulation
  useEffect(() => {
    let animationFrameId: number;
    
    const updatePhysics = () => {
      setLevelL(prev => prev * decaySpeed);
      setLevelR(prev => prev * decaySpeed);
      
      const now = Date.now();
      if (now - peakHoldLRef.current > peakHoldTime) {
        setPeakL(prev => prev * 0.96);
      }
      if (now - peakHoldRRef.current > peakHoldTime) {
        setPeakR(prev => prev * 0.96);
      }
      
      if (isPlaying) {
        // Mock signal generation (synthesizer/pulse simulation to look realistic)
        const time = Date.now() * 0.005;
        const baseSignalL = Math.abs(Math.sin(time) * 0.4 + Math.cos(time * 2.3) * 0.3 + (Math.random() - 0.5) * 0.1);
        const baseSignalR = Math.abs(Math.sin(time * 0.9 + 1.2) * 0.45 + Math.cos(time * 1.8) * 0.25 + (Math.random() - 0.5) * 0.1);
        
        // Logarithmic formula similar to toLevel() in Kotlin:
        // val db = 20 * log10((rms * 3).coerceAtLeast(0.0001f))
        // return ((db + 50f)/50f).coerceIn(0f, 1f)
        const toLevel = (rms: number) => {
          const scaledRms = rms * gain;
          const db = 20 * Math.log10(Math.max(scaledRms, 0.0001));
          return Math.min(Math.max((db + 50) / 50, 0), 1);
        };

        const targetL = toLevel(baseSignalL);
        const targetR = toLevel(baseSignalR);

        setLevelL(prev => Math.max(prev, targetL));
        setLevelR(prev => Math.max(prev, targetR));

        setLevelL(prev => {
          if (prev > peakHoldLRef.current) {
            // we simulate updating peak in same frame
          }
          return prev;
        });

        // Update peaks
        setPeakL(prev => {
          if (targetL > prev) {
            peakHoldLRef.current = Date.now();
            return targetL;
          }
          return prev;
        });

        setPeakR(prev => {
          if (targetR > prev) {
            peakHoldRRef.current = Date.now();
            return targetR;
          }
          return prev;
        });
      }

      animationFrameId = requestAnimationFrame(updatePhysics);
    };

    animationFrameId = requestAnimationFrame(updatePhysics);
    return () => cancelAnimationFrame(animationFrameId);
  }, [isPlaying, decaySpeed, peakHoldTime, gain]);

  const ledCount = 20;

  const renderTower = (level: number, peak: number) => {
    const activeLeds = Math.round(level * ledCount);
    const peakLed = Math.min(Math.max(Math.round(peak * ledCount) - 1, 0), ledCount - 1);
    
    return (
      <div className="flex flex-col gap-1 w-12 bg-slate-900/60 p-2 rounded-lg border border-slate-800/40">
        {Array.from({ length: ledCount }).map((_, i) => {
          const idxFromBottom = ledCount - 1 - i;
          const isOn = idxFromBottom < activeLeds;
          const isRed = idxFromBottom >= 17;
          const isYellow = idxFromBottom >= 13 && idxFromBottom < 17;
          const isPeak = idxFromBottom === peakLed;

          let ledColorClass = "bg-slate-850 border border-slate-900";
          let shadowStyle = {};

          if (isOn) {
            if (isRed) {
              ledColorClass = "bg-red-500 border-red-400";
              shadowStyle = { boxShadow: "0 0 8px rgba(239, 68, 68, 0.6)" };
            } else if (isYellow) {
              ledColorClass = "bg-amber-400 border-amber-300";
              shadowStyle = { boxShadow: "0 0 8px rgba(251, 191, 36, 0.6)" };
            } else {
              ledColorClass = "bg-emerald-400 border-emerald-300";
              shadowStyle = { boxShadow: "0 0 8px rgba(52, 211, 153, 0.6)" };
            }
          }

          return (
            <div
              key={i}
              className="relative w-full h-3 rounded-[2px] transition-all duration-75"
            >
              {/* Normal LED segment */}
              <div 
                className={`w-full h-full rounded-[2px] ${ledColorClass}`}
                style={shadowStyle}
              />
              {/* Peak indicator dot */}
              {isPeak && (
                <div 
                  className="absolute inset-x-[-2px] top-[-1px] bottom-[-1px] rounded-[3px] bg-white border border-slate-100 z-10"
                  style={{ boxShadow: "0 0 10px rgba(255, 255, 255, 0.9)" }}
                />
              )}
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between" id="app_container">
      {/* Top Banner */}
      <header className="border-b border-slate-900 bg-slate-950/80 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <Layers className="w-6 h-6 text-slate-950 font-bold" />
            </div>
            <div>
              <h1 className="font-bold text-lg leading-tight tracking-tight text-white">Stereo VU Overlay</h1>
              <p className="text-xs text-slate-400">Lebegő hangerő kivezérlésmérő Androidra</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
              Kotlin Native Projekt
            </span>
          </div>
        </div>
      </header>

      {/* Main Content Grid */}
      <main className="max-w-6xl mx-auto px-4 py-8 flex-1 grid grid-cols-1 lg:grid-cols-12 gap-8 w-full">
        {/* Left Side: Real-time Interactive Simulator */}
        <div className="lg:col-span-5 flex flex-col gap-6" id="simulator_card">
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/60 backdrop-blur-sm flex flex-col gap-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Sliders className="w-5 h-5 text-teal-400" />
                  Működési Szimulátor
                </h2>
                <p className="text-xs text-slate-400 mt-1">Nézd meg élőben, hogyan reagál a fizikai decay és peak hold</p>
              </div>
              <button
                onClick={() => setIsPlaying(!isPlaying)}
                className={`px-4 py-2 rounded-xl font-medium text-xs flex items-center gap-2 transition-all ${
                  isPlaying 
                    ? 'bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20' 
                    : 'bg-emerald-500 text-slate-950 font-bold hover:bg-emerald-400 hover:scale-[1.02]'
                }`}
              >
                {isPlaying ? (
                  <>
                    <Pause className="w-4 h-4 fill-current" /> Szünet
                  </>
                ) : (
                  <>
                    <Play className="w-4 h-4 fill-current" /> Teszt Hang indítása
                  </>
                )}
              </button>
            </div>

            {/* Display Simulator VU meters */}
            <div className="bg-black/60 rounded-xl p-6 flex flex-col items-center justify-center gap-3 relative border border-slate-900">
              {/* Floating VU View Simulation Box */}
              <div className="flex gap-8 items-stretch relative">
                {/* Left tower */}
                <div className="flex flex-col items-center gap-1">
                  <span className="text-xs font-bold text-slate-400">L</span>
                  {renderTower(levelL, peakL)}
                </div>

                {/* Right tower */}
                <div className="flex flex-col items-center gap-1">
                  <span className="text-xs font-bold text-slate-400">R</span>
                  {renderTower(levelR, peakR)}
                </div>
              </div>
              
              <div className="text-[10px] text-slate-500 text-center mt-2 flex flex-col gap-1">
                <span>Interaktív 60 FPS fizikai render</span>
                <span className="font-mono text-slate-400 text-[11px]">
                  L: {(levelL * 100).toFixed(0)}% (Peak: {(peakL * 100).toFixed(0)}%) | 
                  R: {(levelR * 100).toFixed(0)}% (Peak: {(peakR * 100).toFixed(0)}%)
                </span>
              </div>
            </div>

            {/* Controller Adjustments */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider">Fizikai paraméterek finomhangolása</h3>
              
              {/* Decay speed */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Visszaesési sebesség (Decay)</span>
                  <span className="font-mono text-teal-400">{decaySpeed.toFixed(2)}x</span>
                </div>
                <input
                  type="range"
                  min="0.75"
                  max="0.97"
                  step="0.01"
                  value={decaySpeed}
                  onChange={(e) => setDecaySpeed(parseFloat(e.target.value))}
                  className="w-full accent-teal-400 bg-slate-800 h-1.5 rounded-lg appearance-none cursor-pointer"
                />
                <p className="text-[10px] text-slate-500">Minél magasabb, annál lassabban esik vissza a kijelző oszlopa (Kotlin alapértelmezett: 0.88)</p>
              </div>

              {/* Peak Hold Delay */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Csúcsérték tartás (Peak Hold Delay)</span>
                  <span className="font-mono text-teal-400">{peakHoldTime} ms</span>
                </div>
                <input
                  type="range"
                  min="300"
                  max="2000"
                  step="50"
                  value={peakHoldTime}
                  onChange={(e) => setPeakHoldTime(parseInt(e.target.value))}
                  className="w-full accent-teal-400 bg-slate-800 h-1.5 rounded-lg appearance-none cursor-pointer"
                />
                <p className="text-[10px] text-slate-500">Mennyi ideig tartsa meg a fehér csúcspontot visszaesés előtt (Kotlin alapértelmezett: 900 ms)</p>
              </div>

              {/* Digital Gain Calibration */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Erősítés kalibráció (Digital Gain)</span>
                  <span className="font-mono text-teal-400">{gain.toFixed(1)}x</span>
                </div>
                <input
                  type="range"
                  min="1.0"
                  max="6.0"
                  step="0.2"
                  value={gain}
                  onChange={(e) => setGain(parseFloat(e.target.value))}
                  className="w-full accent-teal-400 bg-slate-800 h-1.5 rounded-lg appearance-none cursor-pointer"
                />
                <p className="text-[10px] text-slate-500">Segít a halkabb zenéknél is kimaxolni a skálát (Kotlin alapértelmezett: 3.0x)</p>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Implementation Architecture & FAQ */}
        <div className="lg:col-span-7 flex flex-col gap-6" id="docs_card">
          {/* Core Response to User */}
          <div className="p-6 rounded-2xl bg-teal-500/5 border border-teal-500/20 backdrop-blur-sm">
            <h2 className="text-lg font-bold text-teal-400 flex items-center gap-2 mb-3">
              <Info className="w-5 h-5 text-teal-400" />
              Az elemzés eredménye: Alapjaiban zseniális az építmény!
            </h2>
            <div className="text-slate-300 text-sm space-y-3 leading-relaxed">
              <p>
                <strong>Igen, a VU meter alapjai abszolút tökéletesek és modernek!</strong> Az alkalmazás a legújabb, Google által javasolt natív Android technológiákra épül:
              </p>
              <ul className="list-disc pl-5 space-y-2 text-xs text-slate-400">
                <li>
                  <strong className="text-teal-300">AudioPlaybackCapture API (Android 10+):</strong> Nem mikrofonból, hanem közvetlenül a rendszer belső digitális hangkártyájáról veszi a hangot, így ha YouTube-ot vagy Spotify-t hallgatsz, külső zajok nélkül, kristálytisztán mér.
                </li>
                <li>
                  <strong className="text-teal-300">Valódi sztereó jelfeldolgozás:</strong> A 16-bites PCM bufferből külön-külön választja le a bal (páros indexek) és a jobb csatorna (páratlan indexek) RMS (átlagos feszültségszint) értékeit.
                </li>
                <li>
                  <strong className="text-teal-300">Logaritmikus dB skálázás:</strong> Az emberi fül logaritmikusan érzékeli a hangerőt, így a lineáris mérés helyett a <code className="bg-slate-900 px-1 py-0.5 rounded text-teal-400">20 * log10(rms)</code> képlettel számol, ami professzionális, élethű dinamikát biztosít.
                </li>
              </ul>
            </div>
          </div>

          {/* New Upgrades Done */}
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/60 flex flex-col gap-4">
            <h3 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider">
              <Zap className="w-4 h-4 text-amber-400" />
              Frissen elvégzett finomítások és javítások
            </h3>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-900">
                <h4 className="text-xs font-bold text-teal-400 mb-1">Double-Tap leállítás</h4>
                <p className="text-xs text-slate-400 leading-relaxed">
                  A lebegő kivezérlésmérőn bárhol duplán koppintva az overlay és a mögötte futó háttérszolgáltatás azonnal és tisztán leáll.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-900">
                <h4 className="text-xs font-bold text-teal-400 mb-1">Értesítési vezérlés</h4>
                <p className="text-xs text-slate-400 leading-relaxed">
                  A rendszerértesítésbe beágyaztunk egy <code className="text-teal-300 text-[11px]">PendingIntent</code>-et. Így ha rákoppintasz az értesítésre, az egész szolgáltatás azonnal leáll.
                </p>
              </div>
            </div>
          </div>

          {/* Quick How-To Instructions */}
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/60 flex flex-col gap-4">
            <h3 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider">
              <Smartphone className="w-4 h-4 text-teal-400" />
              Telepítési & Használati Útmutató
            </h3>

            <div className="space-y-4 text-xs text-slate-400">
              <div className="flex gap-3 items-start">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-teal-400 flex items-center justify-center font-bold shrink-0">1</span>
                <div>
                  <h4 className="font-bold text-white text-sm">Buildelés és APK készítés</h4>
                  <p className="mt-1 leading-relaxed">
                    A forráskód készen áll. Ha pusholod a GitHubodra, az elkészített GitHub Action <code className="bg-slate-950 px-1 py-0.5 rounded text-amber-400">build-apk.yml</code> automatikusan lefordítja és letölthetővé teszi a debug APK fájlt.
                  </p>
                </div>
              </div>

              <div className="flex gap-3 items-start">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-teal-400 flex items-center justify-center font-bold shrink-0">2</span>
                <div>
                  <h4 className="font-bold text-white text-sm">Engedélyek megadása az első indításkor</h4>
                  <p className="mt-1 leading-relaxed">
                    Az app indításakor meg kell adnod a <strong>"Képernyő feletti megjelenítés"</strong> (Overlay) engedélyt, hogy a YouTube előtt lebeghessen, valamint a <strong>"Belső hang rögzítése"</strong> engedélyt a tiszta capture rögzítéshez.
                  </p>
                </div>
              </div>

              <div className="flex gap-3 items-start">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-teal-400 flex items-center justify-center font-bold shrink-0">3</span>
                <div>
                  <h4 className="font-bold text-white text-sm">Zenehallgatás és használat</h4>
                  <p className="mt-1 leading-relaxed">
                    Indítsd el az appot, majd nyisd meg a YouTube vagy kedvenc zenelejátszó alkalmazásodat. A lebegő panelt bárhova elhúzhatod a képernyőn. A leállításhoz csak koppints rá duplán, vagy húzd le az értesítési sávot és nyomj rá az aktív értesítésre.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Linux Mint FAQ */}
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/60 flex flex-col gap-4">
            <h3 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider">
              <Terminal className="w-4 h-4 text-teal-400" />
              Linux (Mint) Kompatibilitás és Csomagolás
            </h3>
            <div className="text-slate-300 text-sm space-y-3 leading-relaxed">
              <p>
                <strong>Igen, elkészítettem a natív Linuxos változatot is!</strong> Közvetlenül a repóban, a <code className="bg-slate-900 px-1 py-0.5 rounded text-teal-400">linux/</code> mappában megtalálod a natív, keret nélküli lebegő ablakos verziót.
              </p>
              <ul className="list-disc pl-5 space-y-2 text-xs text-slate-400">
                <li>
                  <strong className="text-teal-300">Önálló asztali alkalmazás (PyQt6):</strong> A Cinnamon deskletek (CJS / JavaScript) teljesítménye nem az igazi másodpercenként 60-szor frissülő zenei jelfeldolgozáshoz. Ehelyett írtam egy <strong>Python + PyQt6</strong> alkalmazást (PulseAudio/PipeWire támogatással), amely szintén egy mindig felül lévő (Always on Top), áttetsző és keret nélküli ablak (mint Androidon), ráadásul GNOME és KDE alatt is tökéletesen megy!
                </li>
                <li>
                  <strong className="text-teal-300">Használat & Beállítások:</strong> A <code className="bg-slate-900 px-1 py-0.5 rounded text-teal-400">linux/stereo_vu.py</code> fájlt helyileg is futtathatod (a <code className="bg-slate-900 px-1 py-0.5 rounded text-teal-400">requirements.txt</code> függőségek telepítése után). A lebegő ablakot bal gombbal tudod húzni, <strong>jobb gombbal pedig előhozni a Beállítások (Settings) menüt</strong>, ahol az Android verzióhoz hasonlóan állíthatod a témákat, méretet, átlátszóságot, sebességet és a Peak LED-et, vagy bezárhatod a programot.
                </li>
                <li>
                  <strong className="text-teal-300">GitHub Workflows beállítva:</strong> Készítettem egy <code className="bg-slate-900 px-1 py-0.5 rounded text-amber-400">build-linux.yml</code> GitHub Action-t is. Amint fellövöd (push) a kódokat a GitHubra, a szerver automatikusan lefordítja a Python kódot egyetlen, futtatható bináris (PyInstaller) fájlba, így a terminálos bűvészkedést is megúszod, és rögtön letöltheted az Artifact-ot!
                </li>
              </ul>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 py-6 text-center text-xs text-slate-500">
        <div className="max-w-6xl mx-auto px-4 flex flex-col md:flex-row items-center justify-between gap-4">
          <p>© 2026 Stereo VU Overlay. Minden jog fenntartva.</p>
          <div className="flex gap-4">
            <span className="hover:text-slate-300">Android 10+ (API 29) Kompatibilis</span>
            <span>•</span>
            <span className="hover:text-slate-300">Kotlin Native & Jetpack API</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
