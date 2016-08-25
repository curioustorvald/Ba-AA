# BA-AA #

## About ##

This is yet another crudely-written ascii-art generator and demo player that is highly configurable. Default setting of this demo emulates EGA text mode, with four shades of black/white, and a Sound Blaster.

The video playback can be scaled to any console text mode size, can support any video framerate and any length.

You can alter the font set to whatever you want, the AA engine will adjust itself accordingly.

You can also make this demo to play completely different video and audio. Please refer to the ```config.properties``` file.

The demo is distributed under the MIT License. Please refer to ```COPYING``` for the copy of the license.

One last note: don’t expect much about the performance, and don’t try to find out what “BA” means.


## How to run ##

### First run ###

Before you play the demo, please unzip the ```assets.zip```. There should be ```assets``` directory created.

If you don't see the zip file, get one from latest __[release](https://github.com/minjaesong/Ba-AA) (github)__

### Execution ###

Execute __BaAA.jar__ by double-clicking on it or using terminal. Ignore anything else; they’re just bunch of libraries, natives and resources. If you’re not a programmer, just leave them as is.

Java SE version 8 or higher is required.


### Player control ###

- SPACE: pause/resume (frequent pausing will make audio off-sync)
- F1: remove colour filter
- F2: Colour filter IBM (green)
- F3: Colour filter AMBER
- F4: User-defined colour filter
- H: Capture current screen into PNG
- T: Capture current screen into TXT (```bSingleTone=true``` only)


## Configuration ##

If you’re done with the default configuration, it’s time to play with it!

When playing the demo, you can see __STRM__ or __PRLD__ on the window title. This indicates whether the video is streamed or preloaded. If streaming video is too choppy, you can try preload by setting ```bPreloadFrames``` to ```true```, or pre-record everything beforehand. See section __Record and play__ for instruction.

Note that the audio is always streamed from disk.


### How to read window title for information ###

    AA Player — S: 61(60) — M: 216M/1820M — F: 4932/13159 STRM — C: 4 — A: 12
    
- __S__ indicates the current framerate: drawing framerate and video framerate. In this line, 61 is the drawing framerate and 60 is the video’s framerate.
- __M__ indicates the memory usage information. Former is currently used by program (NOT by the Java applet), latter is the maximum amount of memory JVM can use in this applet.
- __F__ indicates the index of frame currently playing. STRM indicates that each frame is streamed from the disk, calculated on the fly, PRLD indicates every frame is pre-loaded and pre-calculated, RECD indicates the frame is loaded from the record file.
- __C__ indicates the number of greyscale tones currently using
- __A__ indicates the current algorithms using. First number represents antialiasing, second is dithering


To changed the font colour, you’ll need to edit font files with your image editor.

- ```font.png```, ```font.3.png```: full brightness (0xFF)
- ```font.1.png```: 33.3% (0x55)
- ```font.2.png```: 66.7% (0xAA)

- ```font.4.png```:  (0x11)
- ```font.5.png```:  (0x22)
- ```font.6.png```:  (0x33)
- ```font.7.png```:  (0x44)
- ```font.8.png```:  (0x66)
- ```font.9.png```:  (0x77)
- ```font.10.png```: (0x88)
- ```font.11.png```: (0x99)
- ```font.12.png```: (0xBB)
- ```font.13.png```: (0xCC)
- ```font.14.png```: (0xDD)
- ```font.15.png```: (0xEE)

(fonts are pre-coloured to make the demo run faster)


## About Ascii Art Library ##

Source: ```ImageToAA*.kt```

The library receives current font (as a SpriteSheet), apply colours, then calculate the luminosity of each glyph. No hand-crafted things other than certain glyphs are go unused.

The object must be initialised with ```setProp()``` function before use, and ```precalcFont()``` must be called before you can actually play with ```toAscii()```.

The library supports Gamma Correction, but not the inverted mode (white background, black text)


## Trivia ##

* The demo plays real 60-frame Bad Apple footage which was generated with SVPFlow.

* Included audio faithfully emulates what the real Sound Blaster would do, sampling rate of 23 kHz, 8-bit, monoaural, then encoded with Vorbis ```-q 0```

* You can use your own series of images, frame size unlimited. (disk space and your GPU texture size has limit, however)

* Built-in font “terminal.png” was from OSX’s single mode console. Didn’t dumped; it was a manual labour of tracing dot by dot. Non-ASCII letters are my own drawing.

* Supported formats for frame: png (rgb or indexed), jpg, bmp, tga (uncompressed)

* Supported formats for audio: ogg, mod, xm (please note that MOD/XM playback of IBXM is suck)


## Disclaimer ##

* I claim no copyright to the included assets of:
    - The audio “Bad Apple!! (feat. nomico)” by Alstroemeria Records, original composition by ZUN
    - The video, frame-doubled version of ```www.nicovideo.jp/watch/sm8628149```

These assets are copyrighted to the its copyright owner.

* Included OGG audio has low sound quality, which is intentional to achieve the retro vibe (Sound Blaster). If you don’t like it, get your own high quality audio and encode with OGG/Vorbis ```-q 10```.

## (dropped features) ##

* Play from mp4: Tried JCodec, playback was very jittery

* Inverted colour (white background): I’m exhausted. You clone it and try it ;)

## How to use my own video and audio ##

To play your own video on the demo player, you’ll need to prepare them yourself.

* Requirements: Your video file, FFmpeg, framerate for the video, Audacity

1. Open FFmpeg, type ```ffmpeg -i input_file_name -vf fps=video_framerate out output_directory/prefix_of_your_own%08d.png```
  e.g. If I’m generating from Test.mp4, ```ffmpeg -i test.mp4 -vf fps=30 out test%08d.png```
2. Extract audio stream from your video file. Google ```video_container demux audio``` (e.g. ```mp4 demux audio```) for instruction.
3. Transcode extracted audio to OGG/Vorbis. You cau use Audacity for the job.
4. Move directory that contains thousands of PNGs and converted OGG to demo’s ```assets``` directory.
5. Configure the demo accordingly. You’ll need to change ```iVideoFramerate```, ```sFramesDir```, ```sFramesPrefix``` and ```sAudioFileName```.

## Record and replay ##

If ```bIsRecordMode``` is set to ```true```, the demo will record the frame into your hard disk.

Name for the file is auto-generated in the format of ```[framename]_[fontname]_[width]x[height]_C[colours]_A[algorithm].aarec```
e.g. ```Bad_Apple_CGA.png_132x74_C4_A11.aarec```

To play from the record, specify ```sRecordFileName``` as the filename of the aarec file. Note that you still have to give valid values in the config to the following:

- sAudioFileName
- sFontFamilyName
- sFontSize

### Pre-recorded files ###

The distribution of the demo will contain 5 aarec files which are:

- ```2ndreal_CGA.png_132x74_C4_A23_fullcp.aarec``` : the demo _Second Reality_ by Future Crew, in four greyscales, full code page. Use audio ```2ndreal.ogg```
- ```2ndreal_CGA.png_132x74_C16_A23_fullcp.aarec``` : the demo _Second Reality_ by Future Crew, in sixteen greyscales, full code page. Use audio ```2ndreal.ogg```
- ```2ndreal_CGA.png_132x74_C4_A13.aarec``` : the demo _Second Reality_ by Future Crew, in four greyscales, only using ASCII glyphs. Use audio ```2ndreal.ogg```
- ```Bad_Apple_CGA.png_76x43_C4_A13.aarec``` : pre-recorded Bad Apple in 76x43, CGA font
- ```Bad_Apple_CGA.png_132x74_C4_A11.aarec``` : pre-recorded Bad Apple in 132x74, CGA font
