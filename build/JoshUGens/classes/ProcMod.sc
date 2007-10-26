/* TO ADD - 
	Two things to add 
	- ability to route output to a virtual bus, then back to a desired bus
	- bility to record ONLY the output of a ProcMod from that virtual bus
	
- if there is routing or recording... should a second group be used? one to wrap the routeting and recording in? Then INSIDE that group, individual note creation? Id so, a ProcMod's group would be the big one to allow for the placing of other Procs before or after it, BUT the individual notes (or the group that is passed into the funciton) needs to be the second, inner group.

WHY? For the first thing, allow multiple Proc to output, let's say, multiple stereo feeds that can
be controlled at a mixer for live mixing of Procs. Second, for recording purposes.
*/

ProcMod {
	var <amp, <>group, <addAction, <target, 
		<timeScale, <lag, <>id, <>function, 
		<>releaseFunc, <>onReleaseFunc, <responder, <envnode, <isRunning = false, <data,
		<starttime, <window, gui = false, <button, <process, retrig = false, isReleasing = false,
		oldgroups, <>clock, <env, <>server, <envbus, <releasetime, uniqueClock = false,
		<tempo = 1, oldclocks;
	classvar addActions;

	*new {arg env, amp = 1, id, group, addAction = 0, target = 1, function, releaseFunc,
			onReleaseFunc, responder, timeScale = 1, lag = 0.01, clock, server;		^super.newCopyArgs(amp, group, addAction, target, timeScale, lag, id, function, 
			releaseFunc, onReleaseFunc, responder).initThisClass(clock, server, env);	}
		
	initThisClass {arg argClock, argServer, argEnv, argNumChannels, argProcout;
		server = argServer ?? {Server.default};
		env = argEnv;
		(env.notNil).if({
			env.isKindOf(Env).if({
				releasetime = env.releaseTime;
				}, {
				env.isKindOf(Number).if({releasetime = env})
				})
			});
		clock = argClock;
		oldgroups = [];
		oldclocks = [];
		}
				
	*play {arg env, amp = 1, id, group, addAction = 0, target = 1, function, 
			releaseFunc, onReleaseFunc, responder, timeScale = 1, lag = 0.01, clock, server;
		^this.new(env, amp, id, group, addAction, target, function, releaseFunc, onReleaseFunc,
			responder, timeScale, lag, clock, server).play;		}

	play {
		var thisfun;
		clock = clock ?? {uniqueClock = true; TempoClock.new(tempo)};
		isRunning.not.if({	
			isRunning = true;
			// add the responder if it isn't nil
			responder.notNil.if({responder.add});
			// create this Proc's group, and if there is an env, start it
			// also, if there is no release node, schedule the Procs release
			group = group ?? {server.nextNodeID};
			env.isKindOf(Env).if({
				envbus = server.controlBusAllocator.alloc(1);
				server.sendBundle(nil,
					[\g_new, group, addActions[addAction], target], 
					[\s_new, \procmodenv_5216, envnode = server.nextNodeID, 0, group, 
						\outbus, envbus, \amp, amp, \timeScale, timeScale, \lag, lag],
					[\n_setn, envnode, \env, env.asArray.size] ++ env.asArray);
				env.releaseNode.isNil.if({
					clock.sched(env.times.sum, {
						this.release; 
						{gui.if({button.value_(0)})}.defer;
						});
					})
				}, {
				server.sendBundle(nil, [\g_new, group, addActions[addAction], target])
				});
			starttime = Main.elapsedTime;
			function.isKindOf(Function).if({
				retrig = true;
				thisfun = function.value(group, envbus, server);
				(thisfun.isKindOf(Routine) || thisfun.isKindOf(Task)).if({
					process = thisfun; this.processPlay;
					})
				}, {
				process = function; this.processPlay;
				})
			})
		}

	tempo_ {arg newTempo;
		tempo = newTempo;
		clock.notNil.if({
			clock.isKindOf(TempoClock).if({
				clock.tempo_(tempo)
				})
			})
		}
			
	processPlay {
		process.reset;
		process.play(clock);
		}
	
	now {
		^Main.elapsedTime - starttime
		}
	
	value {arg recpath;
		this.play(recpath);
	}
	
	amp_ {arg newamp;
		amp = newamp;
		envnode.notNil.if({server.sendBundle(nil, [\n_set, envnode, \amp, amp])});
	}
		
	lag_ {arg newlag;
		lag = newlag;
		envnode.notNil.if({server.sendBundle(nil, [\n_set, envnode, \lag, lag])});
	} 
	
	saveToData {arg anAssociation;
		data.isNil.if({data = Dictionary.new(1)});
		data.add(anAssociation);
		}
		
	env_ {arg newenv;
		isRunning.if({
			"You can not change an envelope while the ProcMod is running".warn;
			}, {
			env = newenv;
			})
	}

	addAction_ {arg newaddAction;
		isRunning.if({
				"You can not change an addAction while the ProcMod is running".warn;
				}, {
				addAction = addActions[newaddAction];
				})
		}
		
	target_ {arg newtarget;
		isRunning.if({
			"You can not change the target while the ProcMod is running".warn;
			}, {
			target = newtarget;
			})
		}
		
	timeScale_ {arg newtimeScale;
		isRunning.if({
			"You can not change the timescale while the ProcMod is running".warn;
			}, {
			timeScale = newtimeScale;
			})
		}
		
	release {arg reltime;
		var curproc, curresp, curgroup, currelfunc, newrelval, curclock, curhdr, curroute;
		curproc = process;
		curresp = responder;
		responder = nil;
		curgroup = group;
		currelfunc = releaseFunc;
		uniqueClock.if({curclock = clock; clock = nil});
		isRunning.if({
			onReleaseFunc.value;
			env.notNil.if({
				newrelval = reltime.notNil.if({
					reltime.neg - 1;
					}, {
					0
					});
				server.sendMsg(\n_set, envnode, \pgate, 0);
				});
			releasetime.notNil.if({
				newrelval = reltime.notNil.if({
					reltime
					}, {
					releasetime
					});
				curclock.sched(newrelval, {
					this.clear(curproc, curresp, curgroup, currelfunc, curclock)
					})
				}, {
				this.clear(curproc, curresp, curgroup, currelfunc, curclock)
				});
			});
		// if retriggerable... clear out group now
		retrig.if({
			oldgroups = oldgroups.add(group);
			oldclocks = oldclocks.add(curclock);
			group = nil; 
			isRunning = false; 
			isReleasing = true;
			});
		}
	
	kill {
		var curproc, curresp, curgroup, currelfunc, oldclock; 
		curproc = process;
		curresp = responder;
		curgroup = group;
		currelfunc = releaseFunc;
		isRunning.if({server.sendMsg(\n_free, curgroup)});
		isReleasing.if({oldgroups.do({arg me; server.sendMsg(\n_free, me)})});
		// if a tempo clock was created for this procMod, clear it
		uniqueClock.if({clock.clear; oldclock = clock; clock = nil});
		oldclocks.do({arg me; me.clear; me.stop});
		curproc.stop;
		this.clear(curproc, curresp, curgroup, currelfunc, oldclock);
		isRunning = false;

	}
	
	// stops the function that is running
	stopprocess {arg oldproc;
		oldproc.stop;
	}

// need to check for TempoClock - old clocks need to be saved until 
// they can be garbage collected
	clear {arg oldproc, oldresp, oldgroup, oldrelfunc, oldclock, oldhdr, oldroute;
		oldproc.notNil.if({this.stopprocess(oldproc)});
		server.sendMsg(\n_free, oldgroup);
		oldgroups.remove(oldgroup);	
		oldrelfunc.notNil.if({
			oldrelfunc.isKindOf(Function).if({
				oldrelfunc.value;
				}, {
				oldrelfunc.reset; oldrelfunc.play(clock)
				})
			});
		oldresp.notNil.if({oldresp.remove});
		retrig.not.if({isRunning = false});
		retrig.if({isReleasing = false});
		oldclock.notNil.if({ oldclock.stop });
		}

	responder_ {arg aResponder;
		responder = aResponder;
		isRunning.if({responder.add});
		}
			
	// ProcMod.gui should create a small GUI that will control the ProcMod - start / stop, amp
	// if trig is notNil, it should be a $char or a midi keynum. This will toggle the ProcMod
	gui {arg bounds = Rect(0, 0, 400, 70), upperLevel = 6, lowerLevel = -inf, parent, trig;
		var slider, numbox, winw, winh, dbspec, xspace, yspace, trigstr;
		gui = true;
		trigstr = trig.notNil.if({"("++trig++")"}, {""});
		window = parent ?? {GUI.window.new(this.id, bounds)};
		winh = bounds.height;
		winw = bounds.width;
		window.view.decorator.isNil.if({
			window.view.decorator = FlowLayout( window.view.bounds, Point(10, 10), Point(10, 10));
			});
		xspace = window.view.decorator.gap.x * 1.5;
		yspace = window.view.decorator.gap.y * 1.5;
		window.front;
		window.onClose_({gui = false; (isRunning || isReleasing).if({this.kill})});
		button = GUI.button.new(window, Rect(0, 0, winw * 0.3 - xspace, winh * 0.8 - yspace))
			.states_([
				["start " ++ this.id ++ trigstr, Color.black, Color(0.3, 0.7, 0.3, 0.3)],
				["stop " ++ this.id ++ trigstr, Color.black, Color(0.7, 0.3, 0.3, 0.3)],
				]);
		isRunning.if({button.value_(1)});
		button.action_({arg me;
				var actions;
				actions = [
					{this.release},
					{this.play}
					];
				actions[me.value].value;
				});
		dbspec = [lowerLevel, upperLevel, \db].asSpec;
		slider = GUI.slider.new(window, Rect(winw * 0.3, 0, winw * 0.5 - xspace, 
				winh * 0.8 - yspace))
			.value_(dbspec.unmap(amp.ampdb).quantize(0.01))
			.action_({arg me;
				var ampval;
				ampval = dbspec.map(me.value);
				this.amp_(ampval.dbamp);
				numbox.value_(ampval.quantize(0.01));
				});
		numbox = GUI.numberBox.new(window, Rect(winw * 0.8, 0, winw * 0.2 - xspace, 
				winh * 0.8- yspace))
			.value_(amp.ampdb.quantize(0.01))
			.action_({arg me;
				this.amp_(me.value.dbamp);
				slider.value_(dbspec.unmap(me.value));
				});
		trig.notNil.if({
			case
				{
					trig.isKindOf(Char)
				} {
					window.view.keyDownAction_({arg view, char;
						(char == trig).if({
							button.valueAction_(button.value + 1)
							})
						})
//				} {
//					trig.isKindOf(Integer)
//				} {
//					
				} {
					true
				} {
					"ProcMod triggers need to be a Char (e.g. $a) or an Integer representing a midi keynum. No trigger has been set-up".warn;
				}
			});
		^this;
		}
	
	*initClass {
		addActions = IdentityDictionary[
			\head -> 0,
			\tail -> 1,
			\before -> 2,
			\after -> 3,
			\replace -> 4,
			0 -> 0,
			1 -> 1,
			2 -> 2,
			3 -> 3,
			4 -> 4
			];	
		StartUp.add({
		SynthDef(\procmodenv_5216, {arg pgate = 1, outbus, amp = 1, timeScale = 1, lag = 0.01;
			var env;
			env = EnvGen.kr(
				Control.names(\env).kr(Env.newClear(30)), pgate, 
					1, 0, timeScale, doneAction: 13) * Lag2.kr(amp, lag);
			Out.kr(outbus, env);
		}).writeDefFile;
		})
	}
}

ProcModR : ProcMod {
	var <routebus, <>procout, <isRecording = false, <notegroup, <numChannels, 
		<>headerFormat = "aiff", <>sampleFormat = "int16", <hdr, oldhdrs;
	classvar addActions;

// if numChannels is not nil, enter routing mode 
//	- unique audio bus (routebus) is passed into the function
// 	- bus is allocated on .play, freed with releaseFunc
// 	- procout tells the routing where to go
	*new {arg env, amp = 1, numChannels = 0, procout = 0, id, group, addAction = 0, target = 1,
			function, releaseFunc, onReleaseFunc, responder, timeScale = 1, lag = 0.01, clock, 
			server;
		^super.newCopyArgs(amp, group, addAction, target, timeScale, lag, id, function, 
			releaseFunc, onReleaseFunc, responder).initThisClass(clock, server, env, 
			numChannels, procout);
		}
		
	initThisClass {arg argClock, argServer, argEnv, argNumChannels, argProcout;
		server = argServer ?? {Server.default};
		env = argEnv;
		(env.notNil).if({
			env.isKindOf(Env).if({
				releasetime = env.releaseTime;
				}, {
				env.isKindOf(Number).if({releasetime = env})
				})
			});
		clock = argClock;
		this.setupRouting(argNumChannels, argProcout);
		oldgroups = [];
		oldclocks = [];
		oldhdrs = [];
		}
				
	*play {arg env, amp = 1, numChannels = 0, procout = 0, id, group, addAction = 0, target = 1, 
			function, releaseFunc, onReleaseFunc, responder, timeScale = 1, lag = 0.01, clock, 
			server, recpath;
		^this.new(env, amp, numChannels, procout, id, group, addAction, target, function, 
			releaseFunc, onReleaseFunc, responder, timeScale, lag, clock, server
			).play(recpath);
		}

	play {arg recpath, timestamp = true;
		var thisfun;
		clock = clock ?? {uniqueClock = true; TempoClock.new(tempo)};
		isRunning.not.if({	
			isRunning = true;
			responder.notNil.if({responder.add});
			notegroup = group = group ?? {server.nextNodeID};
			server.sendBundle(nil, [\g_new, group, addActions[addAction], target]);
			notegroup = server.nextNodeID;
			routebus = server.audioBusAllocator.alloc(numChannels);
			routebus.notNil.if({
				env.isKindOf(Env).if({
					server.sendBundle(nil,
						[\g_new, notegroup, 0, group],
						[\s_new, (\procmodroute_8723_env_ ++ numChannels).asSymbol, 
							envnode = server.nextNodeID, 1, group, \inbus, routebus, 
							\amp, amp, \timeScale, timeScale, \lag, lag, \outbus, procout],
						[\n_setn, envnode, \env, env.asArray.size] ++ env.asArray);
					env.releaseNode.isNil.if({
						clock.sched(env.times.sum, {
							this.release; 
							{gui.if({button.value_(0)})}.defer;
							});
						})
					}, {
					server.sendBundle(nil, 
						[\g_new, notegroup, 0, group],
						[\s_new, (\procmodroute_8723_ ++ numChannels).asSymbol, 
							server.nextNodeID, 1, group, \inbus, routebus, \outbus, procout]);
					});
				}, {
				"A unique audio bus couldn't be allocated for internal routing. This probably isn't what you wanted, and the resulting sound will probably be wrong. You should quit the server, and increase the number of audio busses with ServerOptions. Sorry... but there are limitations".warn;
				});
			recpath.notNil.if({
				hdr = HDR.new(server, Array.fill(numChannels, {arg i; routebus + i}), 3, 
					group, "", recpath ++ id, headerFormat, sampleFormat, 1, timestamp);
				hdr.record;
			});
			starttime = Main.elapsedTime;
			function.isKindOf(Function).if({
				retrig = true;
				thisfun = function.value(notegroup, routebus, server);
				(thisfun.isKindOf(Routine) || thisfun.isKindOf(Task)).if({
					process = thisfun; this.processPlay;
					})
				}, {
				process = function; this.processPlay;
				})
			})
		}

	setupRouting {arg argNumChannels, argProcout;
		(argNumChannels > 0).if({
			numChannels = argNumChannels;
			procout = argProcout.asUGenInput ?? {0};
			});
		}	
		
	numChannels_ {arg num;
		(isRunning.not && isReleasing.not).if({
			this.setupRouting(num);
			}, {
			"The number of channels to route can not be changed while a ProcMod is running or releasing".warn;
			})
		}
					
	release {arg reltime;
		var curproc, curresp, curgroup, currelfunc, newrelval, curclock, curhdr, curroute;
		curproc = process;
		curresp = responder;
		responder = nil;
		curgroup = group;
		currelfunc = releaseFunc;
		curhdr = hdr;
		curroute = routebus;
		uniqueClock.if({curclock = clock; clock = nil});
		isRunning.if({
			onReleaseFunc.value;
			env.notNil.if({
				newrelval = reltime.notNil.if({
					reltime.neg - 1;
					}, {
					0
					});
//				envnode.notNil.if({server.sendMsg(\n_set, envnode, \gate, 0)});
				server.sendMsg(\n_set, group, \pgate, 0);
				});
			releasetime.notNil.if({
				newrelval = reltime.notNil.if({
					reltime
					}, {
					releasetime
					});
				oldhdrs = oldhdrs.add(curhdr);
				curclock.sched(newrelval, {
					this.clear(curproc, curresp, curgroup, currelfunc, curclock,
					curhdr, curroute)
					})
				}, {
				this.clear(curproc, curresp, curgroup, currelfunc, curclock,
					curhdr, curroute)
				});
			});
		// if retriggerable... clear out group now
		retrig.if({
			oldgroups = oldgroups.add(group);
			oldclocks = oldclocks.add(curclock);
			group = nil; 
			isRunning = false; 
			isReleasing = true;
			});
		}
	
	kill {
		var curproc, curresp, curgroup, currelfunc, oldclock, curhdr, curroute;
		curproc = process;
		curresp = responder;
		curgroup = group;
		currelfunc = releaseFunc;
		curhdr = hdr;
		curroute = routebus;
		isRunning.if({server.sendMsg(\n_free, curgroup)});
		isReleasing.if({oldgroups.do({arg me; server.sendMsg(\n_free, me)})});
		uniqueClock.if({clock.clear; oldclock = clock; clock = nil});
		oldclocks.do({arg me; me.clear; me.stop});
		oldhdrs.do({arg me; me.stop});
		curproc.stop;
		this.clear(curproc, curresp, curgroup, currelfunc, oldclock,
			curhdr, curroute, 0.0);
		isRunning = false;
	}

	clear {arg oldproc, oldresp, oldgroup, oldrelfunc, oldclock, oldhdr, oldroute;
		oldproc.notNil.if({this.stopprocess(oldproc)});
		server.audioBusAllocator.free(oldroute);
		oldhdr.notNil.if({
			oldhdr.stop;
			oldhdrs.remove(oldhdr);
		});
		server.sendMsg(\n_free, oldgroup);
		oldgroups.remove(oldgroup);	
		oldrelfunc.notNil.if({
			oldrelfunc.isKindOf(Function).if({
				oldrelfunc.value;
				}, {
				oldrelfunc.reset; oldrelfunc.play(clock)
				})
			});
		oldresp.notNil.if({oldresp.remove});
		retrig.not.if({isRunning = false});
		retrig.if({isReleasing = false});
		oldclock.notNil.if({ oldclock.stop });
		}
	
	*initClass {
		addActions = IdentityDictionary[
			\head -> 0,
			\tail -> 1,
			\before -> 2,
			\after -> 3,
			\replace -> 4,
			0 -> 0,
			1 -> 1,
			2 -> 2,
			3 -> 3,
			4 -> 4
			];
		StartUp.add {
			for(1, 16, {arg i;
					SynthDef((\procmodroute_8723_ ++ i).asSymbol, {arg inbus, outbus;
						Out.ar(outbus, In.ar(inbus, i));
					}).writeDefFile;	
				});
			for(1, 16, {arg i;
					SynthDef((\procmodroute_8723_env_ ++ i).asSymbol, {arg inbus, outbus, 
							pgate = 1, amp = 1, timeScale = 1, lag = 0.01;
						var sig;
						sig = In.ar(inbus, i) *
							EnvGen.kr(
								Control.names(\env).kr(Env.newClear(30)), pgate, 
									1, 0, timeScale, doneAction: 13) * Lag2.kr(amp, lag);
						ReplaceOut.ar(inbus, sig); 
						Out.ar(outbus, sig);
						
					}).writeDefFile;	
				});
		}
	}
}

// proc events should
// - create a GUI for rehearsal purposes -
//		- should include a bank of sliders for each Proc Mod (and identify the event number)
//  		- should be able to save values of amps to a file
//		- MAYBE? allow you to set certain parameters of a ProcMod (like envelope release time, etc)

ProcEvents {
	var <eventDict, <ampDict, <eventArray, <releaseArray, <timeArray, <index,
		<id, gui = false, <window, <server, firstevent = false, initmod, killmod, <amp, <lag,
		<procampsynth, procevscope = false, <pracwindow, <pracmode = false, pracdict, 
		<eventButton, <killButton, <releaseButton, starttime = nil, <pedal = false, 
		<pedalin, <triglevel, <pedrespsetup, <pedresp, <pedalnode, <pedalgui, bounds,
		<bigNum = false, bigNumWindow;
	// below are for timeline functionality. record timestamps of a performance, or playback
	// according to timeStamps of a performance
	var tlrec = false, tlplay = false, <timeLine, <currentTime, <timeOffset = 0.0, 
		<isPlaying, <clock, cper;
	/* begion add */
	var <recordPM = false, recordpath;
	classvar addActions;
	
	*new {arg events, amp, initmod, killmod, id, server, lag = 0.1;
		server = server ?? {Server.default};
		^super.new.init(events, amp, initmod, killmod, id, server, lag);
	}
	
	starttime_ {arg newtime;
		// if starttime is set from the outside:
		starttime = newtime;
		timeOffset = starttime; // for time stamping ProcMod output
		}
		
	init {arg events, argamp, arginitmod, argkillmod, argid, argserver, arglag;
		var proc, release, newproc, evid;
		eventDict = Dictionary.new(events.flat.size);
		eventArray = Array.fill(events.size, {Array.new});
		releaseArray = Array.fill(events.size, {Array.new});
		timeArray = Dictionary.new(events.flat.size);
		id = argid;
		server = argserver;
		amp = argamp;
		index = 0;
		lag = arglag;
		firstevent = true;
		arginitmod.notNil.if({initmod = arginitmod});
		argkillmod.notNil.if({killmod = argkillmod});
		events.do{arg me, i;
			#proc, release = me;
			proc.asArray.flat.do{arg thisev;
				thisev.isKindOf(ProcMod).if({
					thisev.id.isNil.if({ thisev.id = thisev.identityHash.abs.asString });
					eventDict.put(thisev.id, thisev);
					eventArray[i] = eventArray[i].add(thisev.id)
					}, {
					(thisev.isKindOf(Function)).if({
						evid = thisev.identityHash.abs.asString;
						newproc = ProcMod(id: evid).function_(thisev);
						eventDict.put(newproc.id, newproc);
						eventArray[i] = eventArray[i].add(newproc.id);
						}, {
						"ProcEvents needs a ProcMod or Function to work properly".warn;
						});
					});
				};
			release.asArray.do{arg thisrel;
				var id;
				thisrel.isKindOf(ProcMod).if({
					releaseArray[i] = releaseArray[i].add(thisrel.id)
					}, {
					releaseArray[i] = releaseArray[i].add(thisrel)
					})
				}
			};
		// for the GUI if needed
//		bounds = GUI.window.screenBounds;	//
	}
	
	index_ {arg nextIdx;
		index = nextIdx;
		gui.if({AppClock.sched(0.01, {window.view.children[0].value_(nextIdx)})});
		}
		
	next {
		gui.if({
			eventButton.valueAction_(eventButton.value + 1);
			}, {
			this.play(index);
			index = index + 1;			});
	}
	
	record {arg path;
		recordPM = true;
		recordpath = path;
		}
		
	stopRecord {
		recordPM = false;
		}
		
	play {arg event;
		var path;	
		firstevent.if({
			starttime.isNil.if({starttime = Main.elapsedTime});
			initmod.notNil.if({
				recordPM.if({
					path = recordpath ++ (timeOffset + this.now.round(0.001)) ++ initmod.id;
					});
				initmod.value(path);
				}); 
			server.sendMsg(\s_new, \procevoutenv6253, procampsynth = server.nextNodeID, 1,
				0, \amp, amp, \lag, lag);
			firstevent = false;
			});
		eventArray[event].do({arg me;
			timeArray.put(eventDict[me].id, Main.elapsedTime);
			recordPM.if({
				path = recordpath ++ (timeOffset + this.now.round(0.001)) ++ eventDict[me].id;
				});
			eventDict[me].value(path);
			pracmode.if({pracwindow.view.children[pracdict[me]].valueAction_(1);
			})});
		releaseArray[event].do({arg me; eventDict[me].release;
			pracmode.if({pracwindow.view.children[pracdict[me]].valueAction_(0);
			})});
		tlrec.if({timeLine.postln; timeLine.put(event, this.now)});
	}
	
	releaseAll {arg rel = true;
		rel.if({eventDict.do{arg me; me.isRunning.if({me.release})}});
		gui.if({
			window.view.children[2].string_("No events currently running");
			window.view.children[1].focus(true);
			});
		tlplay.if({this.stopTimeLine});
		tlrec.if({this.stopRecordTimeLine});
	}

	reset {
		this.releaseAll(false);
		eventDict.do({arg me; me.isRunning.if({me.kill})});
		server.freeAll;
		initmod.isKindOf(ProcMod).if({initmod.kill});
		firstevent = true;
		index = 0;
		lag = 0.1;
		this.amp_(1);
		gui.if({
			window.view.children[0].value_(0);
			});
		starttime = nil;
		eventDict.do({arg me; me.group = server.nextNodeID});

	}
	
	lag_ {arg newlag;
		lag = newlag;
		server.sendMsg(\n_set, procampsynth, \lag, lag);
		}
		
	// amp should be linear
	amp_ {arg newamp, newlag = 0.1;
		var slide, db;
		amp = newamp;
		(newlag != lag).if({
			this.lag_(newlag);
			});
		server.sendMsg(\n_set, procampsynth, \amp, amp);
		gui.if({
			db = amp.ampdb.round(0.1);
			// convert to the sliders scale
			slide = ControlSpec(-90, 6, \db).unmap(db);
			window.view.children[7].value_(db);
			window.view.children[6].value_(slide);
			});
		}
		
	scope {
		procevscope = true;
		^(server.name == 'internal').if({server.scope}, {"Must use internal server".warn});
	}
	
	killAll {
		eventDict.do{arg me; me.isRunning.if({me.kill})};
		killmod.notNil.if({killmod.value; killmod.kill});
		initmod.notNil.if({initmod.kill});
		gui.if({window.close; gui = false; pracwindow.notNil.if({pracwindow.close})});
		pedal.if({pedrespsetup.remove; pedresp.remove; pedalgui.notNil.if({pedalgui.close})});
		this.reset;
	}
	
	now {arg id;
		var tmp;
		^id.isNil.if({
			Main.elapsedTime - starttime;
			}, {
			tmp = this.timeArray[id];
			tmp.notNil.if({
				Main.elapsedTime - tmp;
				}, {
				"A 'now' was requested for an id that hasn't been played. 0.0 will be used.".warn;
				0.0
				});
			})
		}
	
	starttime {arg id;
		var tmp;
		^id.isNil.if({
			starttime;
			}, {
			tmp = this.timeArray[id];
			tmp.notNil.if({
				tmp - starttime;
				}, {
				"A starttime was requested for an id that hasn't been played. 0.0 will be used.".warn;
				0.0
				})
			})
		}
		
	pedalTrig {arg pedalbus, headroom = 2, trigwindow = 0.5, testdur = 2, guiBounds,
				addAction = 1, target = 0;
			var pedlevel = 0, numlevels, testlevel = 0, 
				testinc = 0;
			var testnode, testid, pedalid, headspec;
			headspec = ControlSpec.new(0, headroom * 10, \lin);
			guiBounds = guiBounds ?? {gui.if({
				Rect(window.bounds.left + window.bounds.width + 10, 
					window.bounds.top,window.bounds.width * 0.3, 
					window.bounds.height * 0.7);
					}, {Rect(10, 10, 144, 288)})};
			server.serverRunning.if({
				pedal = true;
				// poll every 0.1 seconds
				numlevels = (testdur / 0.1).floor;
				testnode = server.nextNodeID;
				testid = server.nextNodeID;
				pedalnode = server.nextNodeID;
				pedalid = server.nextNodeID;
				// the actual responder that will cause pedal triggers to work
				
				pedresp = OSCresponderNode(server.addr, '/tr', {arg time, resp, msg;
					(msg[2] == pedalid).if({
						// if there is a GUI, advance it and run the next function,
						// else, just call .next
						gui.if({
							{eventButton.valueAction_(eventButton.value + 1)}.defer;
							}, {
							this.next;
							});
						});
					});
				
				pedrespsetup = OSCresponderNode(server.addr, '/tr', {arg time, resp, msg;
					(msg[2] == testid).if({
						(testinc < numlevels).if({
							("Pedal setup on bus: " ++ pedalbus ++ " turn: " ++
								 testinc).postln;
							testlevel = testlevel + msg[3];
							testinc = testinc + 1;
							}, {
							server.sendMsg(\n_free, testnode);
							// calc the trig level, and give it some headroom
							testlevel = ((testlevel + msg[3]) / numlevels);
							// start the pedal listening at the head of 0
							server.sendMsg(\s_new, \procevtrig2343, pedalnode, 
								addActions[addAction],
							 	target, \pedalin, pedalbus, \id, pedalid, \level, testlevel,
								\trigwindow, trigwindow, \headroom, headroom);
							// add the pedresp responder
							"Pedal ready".postln;
							pedresp.add;
							{
								pedalgui = GUI.window.new("pedal", guiBounds).front;
								GUI.button.new(pedalgui, Rect(pedalgui.bounds.width * 0.1, 
										pedalgui.bounds.height * 0.02, 
										pedalgui.bounds.width * 0.8, 
										pedalgui.bounds.height * 0.2))
									.states_([
										["Mute Trig", Color.black, 
											Color(0.7, 0.3, 0.3, 0.3)],
										["Allow Trig", Color.black, 
											Color(0.3, 0.7, 0.3, 0.3)]
										])
									.action_({arg me;
										server.sendMsg(\n_set, pedalnode, 
											\mute, (me.value - 1).abs);
										});
								GUI.slider.new(pedalgui, Rect(pedalgui.bounds.width * 0.1, 
										pedalgui.bounds.height * 0.3,
										pedalgui.bounds.width * 0.3, 
										pedalgui.bounds.height * 0.6))
									.value_(headspec.unmap(headroom))
									.action_({arg me;
										var idx, mapval;
										mapval = headspec.map(me.value.round(0.01));
										idx = pedalgui.view.children.indexOf(me);
										pedalgui.view.children[idx + 1].value_(mapval);
										server.sendMsg(\n_set, pedalnode, \headroom, mapval); 
										});
								GUI.numberBox.new(pedalgui, Rect(pedalgui.bounds.width * 0.45, 
										pedalgui.bounds.height * 0.8, 
										pedalgui.bounds.width * 0.4,
										pedalgui.bounds.height * 0.1))
									.value_(headroom)
									.action_({arg me;
										var idx;
										idx = pedalgui.view.children.indexOf(me);
										pedalgui.view.children[idx - 1].value_(
											headspec.unmap(me.value));
										server.sendMsg(\n_set, pedalnode, 
											\headroom, me.value); 

										});
								gui.if({window.front});
							}.defer;
							// remove this repsonder
							pedrespsetup.remove;
							})
						})
					}).add;
				
				server.sendMsg(\s_new, \procevtesttrig76234, testnode, addActions[addAction],
					target, \pedalin, pedalbus, \id, testid);

			}, {"Server isn't booted, pedal trig can't be loaded".warn})
		}
			
	pracGUI {
		var guibounds, stripwidth, stripheight, level;
		this.perfGUI;
		pracmode = true;
		pracdict = Dictionary.new;
		//bounds = SCWindow.screenBounds;
		pracwindow = GUI.window.new(id.asString ++ " practice window", 
			guibounds = Rect(20, 20, bounds.width * 0.9, bounds.height * 0.9)).front;
		stripwidth = (guibounds.width - 10) * 0.066;
		stripheight = (guibounds.height - 40) * 0.25;
		eventArray.flat.do{arg me, i;
			var thisbutton;
			level = (i / 15).floor;
			thisbutton = GUI.button.new(pracwindow,
				Rect(5 + (stripwidth * (i % 15)),
					5 + ((level * stripheight) + (level * 5)),
					stripwidth * 0.9, stripheight * 0.1))
				.states_([
					[me.asString, Color.black, Color(0.3, 0.7, 0.3, 0.7)],
					[me.asString, Color.black, Color(0.7, 0.3, 0.3, 0.7)]
					])
				.action_({arg button;
					var actions;
					firstevent.if({initmod.value; 
						server.sendMsg(\s_new, \procevoutenv6253, 
							procampsynth = server.nextNodeID, 1, 0, \amp, amp);
						firstevent = false});
					actions = [{eventDict[me].release}, {eventDict[me].play}];
					actions[button.value].value;
					});
					
			pracdict.add(me -> pracwindow.view.children.indexOf(thisbutton));
			
			GUI.slider.new(pracwindow,
				Rect(5 + (stripwidth * (i % 15)),
					5 + ((level * stripheight) + (((stripheight * 0.2) + (level *  5)))),
					stripwidth * 0.3, stripheight * 0.6))
				.value_(0.7079)
				.action_({arg slider;
					var val;
					val = ControlSpec(-90, 6, \db).map(slider.value);
					pracwindow.view.children[pracwindow.view.children.indexOf(slider) + 1]
						.value_(val.round(0.01));
					eventDict[me].amp_(val.dbamp);
					});
			
			GUI.numberBox.new(pracwindow,
				Rect(5 + (stripwidth * (i % 15)),
					10 + ((level * stripheight) + (((stripheight * 0.8) + (level * 5)))),
					stripwidth * 0.75, stripheight * 0.125))
				.value_(0)
				.action_({arg number;
					var val;
					val = ControlSpec(-90, 6, \db).unmap(number.value);
					pracwindow.view.children[pracwindow.view.children.indexOf(number) - 1]
						.value_(val);
					eventDict[me].amp_(val);
					});
				

			};
		pracwindow.onClose_({pracmode = false});
		^pracwindow;
		}
		
	bigNumGUI {arg bigNumBounds, fontSize = 96;
		var numstring;
		bigNumBounds = bigNumBounds ?? {Rect(bounds.width * 0.5, bounds.height * 0.5,
			bounds.width * 0.3, bounds.height * 0.3)};
		bigNum = true;
		bigNumWindow = GUI.window.new(id.asString, bigNumBounds);
		bigNumWindow.front;
		bigNumWindow.onClose_({bigNum = false});
		GUI.staticText.new(bigNumWindow, Rect(0, 0, bigNumBounds.width, bigNumBounds.height))
			.string_("No event")
			.font_(Font("Arial", fontSize))
			.align_(\center);
		gui.if({window.front});
		^this;
		}
		
	perfGUI {arg guiBounds, buttonColor = Color(0.3, 0.7, 0.3, 0.7);
		var buttonheight, buttonwidth;
		bounds = GUI.window.screenBounds;	
		
		guiBounds = guiBounds ?? {Rect(10, bounds.height * 0.5, bounds.width * 0.3, 
				bounds.height * 0.3)};
				
		gui = true;
		
		window = GUI.window.new(id.asString, guiBounds);
		
		buttonheight = guiBounds.height * 0.15;
		buttonwidth = guiBounds.width * 0.5;
		
		GUI.numberBox.new(window, Rect(10 + buttonwidth, 10, buttonwidth * 0.9, 
				buttonheight * 0.9))
			.value_(index)
			.font_(Font("Arial", 24))
			.action_({arg me;
				window.view.children[window.view.children.indexOf(me) + 1].focus(true);
				});
			
		eventButton = GUI.button.new(window, Rect(10, 10, buttonwidth * 0.9, 
				buttonheight * 0.9))
			.states_([
				["Next Event:", Color.black, buttonColor]
				])
			.font_(Font("Arial", 24))
			.action_({arg me;
				var numbox, numboxval;
				numbox = window.view.children.indexOf(me)-1;
				numboxval = window.view.children[numbox].value;
				bigNum.if({
					bigNumWindow.view.children[0].string_(numboxval)
					});
				(numboxval < eventArray.size).if({
					this.play(numboxval);
					window.view.children[numbox].value_(numboxval + 1);
					window.view.children[window.view.children.indexOf(me)+1]
						.string_("Current Event: "++numboxval);
					}, {
					"No event at that index".warn
					})
				});
						
		GUI.staticText.new(window, Rect(10, 10 + buttonheight, buttonwidth * 1.8, 
				buttonheight * 0.9))
			.font_(Font("Arial", 24))
			.string_("No events currently running");

		GUI.button.new(window, Rect(10, 10 + (buttonheight * 2), buttonwidth * 0.9, 
				buttonheight * 0.9))
			.states_([
				["Reset", Color.black, buttonColor]
				])
			.font_(Font("Arial", 24))
			.action_({arg me;
				window.view.children[window.view.children.indexOf(me) - 2].focus(true);
				this.reset;
				});				 

		releaseButton = GUI.button.new(window, Rect(10, 10 + (buttonheight * 3), buttonwidth * 0.9, 
				buttonheight * 0.9))
			.states_([
				["Release All", Color.black, buttonColor]
				])
			.font_(Font("Arial", 24))
			.action_({arg me;
				window.view.children[window.view.children.indexOf(me) - 3].focus(true);
				this.releaseAll;
				});

		killButton = GUI.button.new(window, Rect(10, 10 + (buttonheight * 4), buttonwidth * 0.9, 
				buttonheight * 0.9))
			.states_([
				["Kill All", Color.black, buttonColor]
				])
			.font_(Font("Arial", 24))
			.action_({arg me;
				window.view.children[window.view.children.indexOf(me) - 4].focus(true);
				this.killAll;
				});
		
		GUI.slider.new(window, Rect(buttonwidth + 10, 5 + buttonheight * 2, buttonwidth * 0.2,
				buttonheight * 3))
			.value_(ControlSpec(-90, 6, \db).unmap(amp.ampdb))
			.action_({arg me;
				var val;
				val = ControlSpec(-90, 6, \db).map(me.value);
				this.amp_(val.dbamp);
				});
				
		GUI.numberBox.new(window, Rect((buttonwidth * 1.3) + 10, 5 + buttonheight * 2, 
				buttonwidth * 0.4, buttonheight * 0.9))
			.value_(amp.ampdb)
			.font_(Font("Arial", 24))
			.action_({arg me;
				var val;
				val = me.value.dbamp;
				this.amp_(val);
				});
				
		window.view.children[1].focus(true);
		
		window.onClose_({this.killAll; gui = false; bigNum.if({bigNumWindow.close})});
		
		window.front;
		
		^this
		}
	
	/* add time line functionality */	
	saveTimeLine {arg path;
		var fil;
		fil = File.new(path, "w");
		fil.put(timeLine.asString);
		fil.close;
		}
		
	loadTimeLine {arg path;
		var fil, tmp;
		fil = File.new(path, "r");
		tmp = fil.readAllString;
		tmp = tmp.interpret.asFloat; // build some error handling in here;
		timeLine = tmp;
		}
	
	timeLine_ {arg timeLineArray;
		timeLineArray.isKindOf(String).if({
			this.loadTimeLine(timeLineArray)
			}, {
			timeLineArray.isKindOf(Array).if({
				timeLine = timeLineArray.asFloat;
				}, {
				"timeLines should be either a path to a file containing a timeLine, or an array of time stamps".warn})})
			}
		
	withTimeLine {arg timeLineArray, timeLineStart = 0.0;
		var tmp;
		timeLineArray.notNil.if({this.timeLine_(timeLineArray)});
		timeOffset = currentTime = timeLineStart.asFloat;
		this.index_(
			timeLine.indexOf(currentTime) ?? {
				(currentTime < timeLine.last).if({
					tmp = (timeLine.indexOfGreaterThan(currentTime)).max(0.0);
					timeOffset = timeLine[tmp];
					tmp;
					}, {
					"Current time must be less then the total time in the time line".warn;
					0})
				}
			);
		timeLine = [0.0] ++ timeLine;
		}
		
	playTimeLine {arg wait;
		var numEvs;
		tlrec.not.if({
			tlplay = true;
			numEvs = eventArray.size - 1;
			clock = clock ?? {TempoClock.new};
			wait = wait ?? { timeLine[index + 1] - currentTime };
			cper.isNil.if({
				cper = {this.stopTimeLine};
				CmdPeriod.add(cper);
				});
			clock.sched(wait, {
				{this.next}.defer;
				index = index + 1;
				(index <= numEvs).if({
					currentTime = currentTime + wait;
					wait = timeLine[index + 1] - currentTime;
					this.playTimeLine(wait);
					}, {
					this.stopTimeLine
					})
				});
			})
		}
		
	stopTimeLine {
		tlplay = false;
		clock.stop;
		clock = nil;
		CmdPeriod.remove(cper);
		}
				
	recordTimeLine {
		tlplay.not.if({
			timeLine.isNil.if({
				timeLine = Array.newClear(eventDict.size);
				});
			tlrec = true;
			})
		}
		
	stopRecordTimeLine {
		tlrec = false;
		}
		
	*initClass {
		addActions = IdentityDictionary[
			\head -> 0,
			\tail -> 1,
			\before -> 2,
			\after -> 3,
			\replace -> 4,
			0 -> 0,
			1 -> 1,
			2 -> 2,
			3 -> 3,
			4 -> 4
			];
		StartUp.add {	
			SynthDef(\procevoutenv6253, {arg amp = 1, lag = 0.2;
				ReplaceOut.ar(0, In.ar(0, 8) * Lag2.kr(amp, lag))
				}).writeDefFile;	
			
			SynthDef(\procevtesttrig76234, {arg pedalin, id, dur = 2;
					var ped;
					ped = RunningSum.rms(In.ar(pedalin), 0.1 * SampleRate.ir);
					SendTrig.ar(Impulse.ar(10), id, ped);
				}).writeDefFile;
				
			SynthDef(\procevtrig2343, {arg pedalin, id, level = 1, headroom = 1, trigwindow = 1,
					mute = 1;
				var ped;
				ped = In.ar(pedalin) * mute;
				SendTrig.ar(Trig1.ar(ped > (level * headroom), trigwindow), id, 1);
				}).writeDefFile;	
			
			}
	}

}

/* this is predominantly a GUI class that stores ProcMods and displays them in a GUI. The GUI has 
* two parts. A window that shows ProcMods in GUI mode, and a DragSink that takes ProcMods, places
* them in the GUI, and can save them to a file as an archive
*/

ProcSink {
	var <name, initmod, killmod, server, <sinkWindow, <sinkBounds, <procWindow, <bounds, <numProcs,
		<screenWidth, <screenHeight, <procMods, gui = false, <columns, rows, baseheight,
		<>starttime, firstProc = true;

	*new {arg name, sinkBounds, bounds, columns = 3, initmod, killmod, server, parent;
		server = server ?? {Server.default};
		^super.newCopyArgs(name, initmod, killmod, server).init(sinkBounds, bounds, columns, parent);
	}
	
	*loadFromFile {arg path;
		^ProcSink.readArchive(path).initFromFile;
		}
	
	init {arg argSinkBounds, argBounds, argColumns, argParent;
		procMods = Array.new;
		numProcs = 0;
		columns = argColumns;
		rows = 1;
		screenWidth = GUI.window.screenBounds.width;
		screenHeight = GUI.window.screenBounds.height;
		sinkBounds = argSinkBounds ?? {Rect(10, screenHeight * 0.6, screenWidth * 0.1 - 20, 
			screenHeight * 0.2 - 20)};
		sinkWindow = GUI.window.new("ProcSink", sinkBounds).front;
		GUI.dragSink.new(sinkWindow, Rect(5, 5, sinkBounds.width - 10, sinkBounds.height * 0.6))
			.action_({arg me;
				this.add(me.object)
				});
		GUI.button.new(sinkWindow, Rect(5, sinkBounds.height * 0.7, sinkBounds.width - 10,
				sinkBounds.height * 0.3 - 10))
			.states_([
				["Start piece", Color.black, Color(0.7, 0.3, 0.3, 0.3)],
				["Stop piece", Color.black, Color(0.7, 0.3, 0.3, 0.3)],
				])
			.action_({arg me;
				var actions;
				actions = [
					{this.kill},
					{this.start}
					];
				actions[me.value].value;
				});
		sinkWindow.onClose_({this.kill});
		sinkWindow.view.children[1].focus(true);
		bounds = argBounds ?? {Rect(screenWidth * 0.1, screenHeight * 0.9, 
			screenWidth * 0.9, screenHeight * 0.1)};
		baseheight = bounds.height;
		this.gui(argParent);
		}
	
	gui {arg parent;
		procWindow = parent ?? {GUI.window.new(name, bounds).front};
		procWindow.view.decorator = 
			FlowLayout(procWindow.view.bounds, Point(10, 10), Point(10, 10));
		procWindow.onClose_({this.kill});
		sinkWindow.front;
		}
		
	start {
		procWindow.front;
		initmod.notNil.if({initmod.play});
		starttime = Main.elapsedTime;
		}
	
	now {
		^(Main.elapsedTime - starttime)
		}
		
	kill {
		{
			procWindow.isClosed.not.if({procWindow.close});
			sinkWindow.isClosed.not.if({sinkWindow.close});
		}.defer;
		procMods.do({arg me; (me.isRunning).if({ me.kill })});
		killmod.notNil.if({killmod.play});
		}
		
	add {arg proc, upperLevel = 6, lowerLevel = -inf; 
		numProcs = numProcs + 1;
		procMods = procMods.add(proc);
		rows = (numProcs / columns).ceil;
		{
		procWindow.bounds_(Rect(procWindow.bounds.left, procWindow.bounds.top, 
			procWindow.bounds.width, rows * baseheight - procWindow.view.decorator.gap.x));
		proc.gui(Rect(0, 0, procWindow.bounds.width / columns, baseheight), upperLevel, 
			lowerLevel, procWindow)}.defer;
		// why does this need to be contiually reset?
		procWindow.onClose_({this.kill});
	}
			

}

