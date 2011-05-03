// car is "pitch you hear" (like a saw wave, or sound), mod is voice, num is number of bands

		hf = HPF.ar(HPF.ar( mod, hpf), hpf);


	*bark {arg signal, input, mull = 10;

                var sourceAmp, freqArray, bwArray;

                freqArray = [50, 150, 250, 350, 450, 570, 700, 840, 1000,
						1170, 1370, 1600, 1850,
					2150, 2500, 2900, 3400, 4000,
						4800, 5800, 7000, 8500, 10500, 13500];
                sourceAmp = Amplitude.kr(input);
                bwArray =
						[50,50,50,50,60,70,80,80,100,110,130,150,180,200,250,400,500,600,700,
					1000,1500,2000,3500,3500];
                ^Mix.arFill ( freqArray.size, {arg i;
                        var output, freq, bandWidth;
                        freq = freqArray.at(i);
                        bandWidth = bwArray.at(i);
                        output = BPF.ar(signal, freqArray.at(i),bandWidth/freq, sourceAmp)*mull
                })
        }
     }