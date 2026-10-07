# Pass 2 benchmark results

Executed 2026-10-07 on Windows / AMD Ryzen 5 5600G, six cores/twelve logical cores, 33,687,609,344 bytes physical RAM (31.4 GiB). CPU inference uses four threads. Models, runtime revisions, quantization, rights, sizes and file hashes are in [candidate records](MODEL_CANDIDATES.md) and the [artifact lock](../../tools/offline-feasibility/artifacts.lock.json).

## Procedure and measurement limits

Two complete cached offline runs passed: [eSpeak/formant evidence](evidence/desktop-espeak.json) and [Piper/neural evidence](evidence/desktop-piper.json). Each translates 32 unique cases (16 per direction) plus the 16 English cases with the compact Romance alternate: 48 outputs/run. Six clean locally synthesized recordings plus two deterministic 10 dB Gaussian-noise variants run through Tiny and Base: 16 full speech pipelines/run, 32 in total. There are no personally sensitive recordings.

These are single-pass engineering observations, not statistical confidence intervals or fluent/native-speaker approval. Text translation uses greedy Marian decoding, no KV cache, max 256 input pieces/max 96 output tokens. First formant run used Whisper CLI default beam/best-of 5 with fallback; the neural run uses greedy best-of 1/no fallback. Consequently, cross-run ASR differences are confounded by decoding and fixture changes. Final reproduction uses greedy for both fixture engines.

Timings are wall-clock milliseconds. Desktop pipeline totals include cold ASR loading, warm translation inference, and cold TTS engine/voice loading; translator load is recorded separately and excluded. TTS separate model-load time is UNAVAILABLE. Speech duration is 3.52–4.14 seconds for neural fixtures and 3.71–4.97 seconds for formant fixtures. Python samples process-tree RSS every 10 ms, with shared pages potentially counted twice. This is not Android PSS or a phone RAM requirement. Busy CPU cores are approximate process CPU-seconds / wall-seconds, not percentage of the whole machine.

## Text timing and memory

| Fixture run | Translator | Cold load ms | Median translation ms | Peak RSS MiB |
| --- | --- | ---: | ---: | ---: |
| Formant | en-es | 500.8 | 567.4 | 529.7 |
| Formant | es-en | 545.7 | 554.3 | 531.6 |
| Formant | en-romance | 1121.0 | 952.7 | 533.7 |
| Neural | en-es | 981.7 | 607.2 | 529.4 |
| Neural | es-en | 474.0 | 492.0 | 531.7 |
| Neural | en-romance | 471.0 | 549.8 | 535.1 |

MT cold-loaded parent RSS was about 239–243 MiB; inference expanded runtime arenas to roughly 529–535 MiB in the neural run. Only one translator pair is intentionally active at a time. These figures include interpreter/runtime/tokenizer overhead, not just weight memory.

## Full desktop speech pipeline

| Fixture | Direction | ASR | Median ASR incl. load ms | Median MT ms | Median TTS incl. load ms | Median total ms | Peak RSS MiB | Mean WER | Approx. busy cores |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Formant | en-es | tiny | 876.9 | 641.9 | 121.8 | 1612.9 | 675.5 | 0.384 | 3.6 |
| Formant | en-es | base | 2388.0 | 565.7 | 111.5 | 3255.7 | 742.2 | 0.087 | 3.4 |
| Formant | es-en | tiny | 983.8 | 668.4 | 97.7 | 1785.6 | 680.1 | 0.763 | 3.9 |
| Formant | es-en | base | 2461.2 | 885.5 | 163.3 | 3688.2 | 745.8 | 0.532 | 3.1 |
| Neural | en-es | tiny | 515.5 | 495.7 | 563.8 | 1581.6 | 647.3 | 0.086 | 3.8 |
| Neural | en-es | base | 1008.0 | 466.7 | 619.7 | 2115.0 | 696.0 | 0.040 | 3.5 |
| Neural | es-en | tiny | 571.3 | 555.2 | 955.1 | 2134.9 | 703.6 | 0.258 | 3.6 |
| Neural | es-en | base | 1004.7 | 542.0 | 905.4 | 2478.8 | 705.5 | 0.150 | 3.6 |

Neural-run ASR reported cold-load medians: Tiny EN 56.1 ms / ES 56.6 ms; Base EN 76.9 ms / ES 75.8 ms. Wall ASR includes process startup and inference. Initial formant logs suppressed the separate load measurement: UNAVAILABLE, not zero. WER normalizes case/punctuation and uses word edit distance; it can exceed 1 with many insertions and is not translation accuracy.

## All text cases and outputs

Outputs below are from the neural-fixture run; text inference is independent of the fixture generator. Exact initial-run outputs/timings also remain in its JSON. The meaning column is the intended engineering check, not an externally certified reference translation.

| ID / direction / category | Source | Meaning that must survive | Baseline output | Romance alternate (EN only) |
| --- | --- | --- | --- | --- |
| en01 / en-es / conversation | I can pick you up after work, but I cannot stay for dinner. | Pickup after work; dinner refusal; negation. | Puedo recogerte después del trabajo, pero no puedo quedarme a cenar. | Puedo recogerte después del trabajo, pero no puedo quedarme a cenar. |
| en02 / en-es / slang | That repair cost an arm and a leg. I am pretty broke now. | Very expensive repair; little money; not literal limbs. | Esa reparación costó un brazo y una pierna. | Esa reparación cuesta un brazo y una pierna. |
| en03 / en-es / idiom | Let's call it a day and finish the fence tomorrow. | Stop work today; finish fence tomorrow. | Terminemos mañana con la valla. | Terminemos el día y acabemos la cerca mañana. |
| en04 / en-es / negation | Do not start the engine until I remove the jumper cables. | Prohibition until cables removed; safety condition. | No encienda el motor hasta que retire los cables del puente. | No arranque el motor hasta que yo retire los cables del súper. |
| en05 / en-es / numbers-money | The total is 127 dollars and 50 cents, not 175 dollars. | 127.50 dollars; reject 175. | El total es 127 dólares y 50 centavos, no 175 dólares. | El total es 127 dólares y 50 centavos, no 175 dólares. |
| en06 / en-es / date-time | The delivery is on October 12 at 3:30 in the afternoon. | October 12; 15:30; delivery. | La entrega es el 12 de octubre a las 3:30 de la tarde. | La entrega es el 12 de octubre a las 3:30 de la tarde. |
| en07 / en-es / measurement | Cut two boards, each 45 inches long, and leave a quarter-inch gap. | Two boards; 45 inches each; gap 0.25 inch. | Corta dos tablas, cada una de 45 pulgadas de largo, y deja un hueco de un cuarto de pulgada. | Cortar dos tablas, cada 45 pulgadas de largo, y dejar un cuarto de pulgadas de espacio. |
| en08 / en-es / automotive | The truck cranks but won't start, and the check-engine light is on. | Starter turns; engine will not run; warning lamp. | El camión se pone de manivela pero no arranca, y la luz del motor de verificación está encendida. | El camión se pone a funcionar pero no se pone a funcionar, y el motor de control está encendido. |
| en09 / en-es / automotive | The brake pedal feels soft after we replaced the master cylinder. | Spongy brake pedal; master cylinder replacement. | El pedal del freno se siente suave después de que sustituimos el cilindro maestro. | El pedal del freno se siente suave después de que reemplazamos el cilindro maestro. |
| en10 / en-es / construction | Shut off the breaker before you replace that outlet. | Disconnect circuit breaker before electrical outlet work. | Apaga el interruptor antes de reemplazar esa salida. | Apaga el interruptor antes de reemplazar esa toma. |
| en11 / en-es / construction | The concrete needs to cure before we remove the forms. | Concrete curing; remove formwork later. | El concreto necesita curarse antes de que eliminemos las formas. | El concreto necesita curar antes de quitar las formas. |
| en12 / en-es / ranch | Move the calves to the north pasture and check the water trough. | Young cattle; north pasture; drinking trough inspection. | Mueva los terneros al pasto norte y revise el abrevadero. | Mueva los becerros al pasto norte y verifique el agua. |
| en13 / en-es / agriculture | The irrigation pump lost its prime, so the field is not getting water. | Pump priming lost; field not irrigated. | La bomba de riego perdió su máxima potencia, así que el campo no está recibiendo agua. | La bomba de riego perdió su primer, por lo que el campo no está recibiendo agua. |
| en14 / en-es / incomplete | If the hose is still leaking, maybe just... wait, close the valve first. | Interrupted plan; close valve first; hose leak condition. | Si la manguera sigue goteando, tal vez... espere, cierre la válvula primero. | Si la manguera sigue saliendo, tal vez... espera, cierra la válvula primero. |
| en15 / en-es / ambiguous | Put the jack next to the tire, not by the river bank. | Vehicle lifting jack; tire; river bank negated. | Pon la jota junto a la rueda, no junto a la orilla del río. | Pon la jack junto a la rueda, no junto a la orilla del río. |
| en16 / en-es / difficult | I didn't say he stole the tools; I said nobody saw him return them. | No accusation of theft; nobody witnessed return. | No dije que robó las herramientas; dije que nadie lo vio devolverlas. | No dije que él robó las herramientas; dije que nadie lo vio devolverlas. |
| es01 / es-en / conversation | Te puedo llevar después del trabajo, pero hoy no me puedo quedar a cenar. | Ride after work; cannot stay for dinner today. | I can take you after work, but I can't stay for dinner today. | NOT RUN (English-source alternate) |
| es02 / es-en / regional-slang | Ahorita no traigo feria; te pago el viernes. | Casual Mexican Spanish: no cash now; payment Friday. | I don't have a fair right now; I'll pay you Friday. | NOT RUN (English-source alternate) |
| es03 / es-en / idiom | Me estás tomando el pelo o de verdad se descompuso otra vez? | Are you teasing me; did it actually break again? | Are you kidding me or did it really break down again? | NOT RUN (English-source alternate) |
| es04 / es-en / negation | No prendas el motor hasta que quite los cables para pasar corriente. | Do not start until jumper cables removed. | Don't wear the engine until you remove the cables to pass current. | NOT RUN (English-source alternate) |
| es05 / es-en / numbers-money | Son ciento veintisiete dólares con cincuenta centavos, no ciento setenta y cinco. | 127.50 dollars; not 175. | That's $127 with fifty cents, not $175. | NOT RUN (English-source alternate) |
| es06 / es-en / date-time | La entrega es el doce de octubre a las tres y media de la tarde. | October 12; 15:30; delivery. | The delivery is on October 12th at 3:30 p.m. | NOT RUN (English-source alternate) |
| es07 / es-en / measurement | Necesito tres litros de aceite y una manguera de dos metros. | Three liters oil; two-meter hose. | I need three liters of oil and a two-meter hose. | NOT RUN (English-source alternate) |
| es08 / es-en / automotive | La camioneta da marcha, pero no arranca y está prendida la luz del motor. | Starter cranks; no engine start; engine warning lamp. | The van is running, but it's not starting and the engine light is on. | NOT RUN (English-source alternate) |
| es09 / es-en / automotive | El pedal del freno se siente esponjoso desde que cambiamos la bomba de freno. | Spongy brake pedal; master cylinder replaced. | The brake pedal feels fluffy since we changed the brake pump. | NOT RUN (English-source alternate) |
| es10 / es-en / construction | Baja el interruptor antes de cambiar ese contacto. | Circuit off before electrical outlet replacement; regional contacto. | Put the switch down before changing that contact. | NOT RUN (English-source alternate) |
| es11 / es-en / construction | Hay que dejar curar el concreto antes de quitar la cimbra. | Concrete cure first; regional formwork cimbra. | You have to let the concrete heal before removing the cipher. | NOT RUN (English-source alternate) |
| es12 / es-en / ranch | Lleva los becerros al potrero del norte y revisa el bebedero. | Calves; north pasture; water trough. | Take the calves to the north pasture and check the trough. | NOT RUN (English-source alternate) |
| es13 / es-en / agriculture | La bomba de riego perdió el cebado y no está llegando agua al cultivo. | Lost pump prime; crop not receiving water. | The irrigation pump lost the primer and no water is coming into the crop. | NOT RUN (English-source alternate) |
| es14 / es-en / incomplete | Si todavía gotea la manguera, mejor... no, espera, cierra primero la válvula. | Interrupted suggestion; first close valve; leaking hose. | If the hose still leaks, better... no, wait, close the valve first. | NOT RUN (English-source alternate) |
| es15 / es-en / ambiguous | Deja el gato junto a la llanta; estoy hablando de la herramienta, no del animal. | Vehicle jack, not cat; next to tire. | Leave the cat next to the rim; I'm talking about the tool, not the animal. | NOT RUN (English-source alternate) |
| es16 / es-en / difficult | No dije que se robó las herramientas; dije que nadie lo vio devolverlas. | No theft accusation; return not witnessed. | I didn't say he stole the tools; I said no one saw him return them. | NOT RUN (English-source alternate) |

## Quality observations

- Straight conversation, the tested amounts/dates/units, and the explicit accusation/return negation generally preserve their main meaning. This small corpus does not establish general numeric or negation reliability.
- EN02 literally translates “arm and a leg” and drops the entire second sentence about having no money in both candidates. EN03 baseline loses stopping work today. The alternate improves EN03/EN10 but worsens other cases; it is not a clear winner.
- EN04/ES04 mishandle jumper cables; ES04 says “Don't wear the engine…” even with perfect text input. EN08/ES08 confuse starter cranking with running. EN10/ES10 lose electrical-outlet terminology. EN11/ES11 mishandle formwork (“cipher” for cimbra). EN13/ES13 lose pump priming. EN15/ES15 confuse jack with a letter/card or cat despite context. These are material product failures in the requested work domains.
- Mexican casual “feria” becomes “fair” in ES02. Spain Spanish TTS and this small synthetic set do not prove Mexican/Latin-American regional speech coverage.
- Engineering verdict: NEEDS MORE EVALUATION. Basic conversation is useful enough to justify further research, but this configuration is not suitable for dependable technical/safety instructions.

## Separate ASR and translation failures

| Neural sample | ASR | WER | Recognized text | Translation of recognized text | Translation of perfect source text |
| --- | --- | ---: | --- | --- | --- |
| en01-clean | tiny | 0.077 | I could pick you up after work, but I cannot stay for dinner. | Podría recogerte después del trabajo, pero no puedo quedarme a cenar. | Puedo recogerte después del trabajo, pero no puedo quedarme a cenar. |
| en04-clean | tiny | 0.000 | do not start the engine until I remove the jumper cables. | No encienda el motor hasta que retire los cables de puente. | No encienda el motor hasta que retire los cables del puente. |
| en04-noise10db | tiny | 0.182 | Do not start the engine until I remove the gel for cables. | No encienda el motor hasta que retire el gel para cables. | No encienda el motor hasta que retire los cables del puente. |
| en12-clean | tiny | 0.083 | Move the car to the north pasture and check the water trough. | Mueva el coche al pasto norte y revise el abrevadero. | Mueva los terneros al pasto norte y revise el abrevadero. |
| en01-clean | base | 0.077 | I could pick you up after work, but I cannot stay for dinner. | Podría recogerte después del trabajo, pero no puedo quedarme a cenar. | Puedo recogerte después del trabajo, pero no puedo quedarme a cenar. |
| en04-clean | base | 0.000 | Do not start the engine until I remove the jumper cables. | No encienda el motor hasta que retire los cables del puente. | No encienda el motor hasta que retire los cables del puente. |
| en04-noise10db | base | 0.000 | Do not start the engine until I remove the jumper cables. | No encienda el motor hasta que retire los cables del puente. | No encienda el motor hasta que retire los cables del puente. |
| en12-clean | base | 0.083 | Move the carts to the north pasture and check the water trough. | Mueva los carros al pasto norte y revise el abrevadero. | Mueva los terneros al pasto norte y revise el abrevadero. |
| es01-clean | tiny | 0.071 | te puede llevar después del trabajo, pero hoy no me puedo quedar a cenar. | I can take you after work, but I can't stay for dinner today. | I can take you after work, but I can't stay for dinner today. |
| es04-clean | tiny | 0.083 | no prendas en motor hasta que quite los cables para pasar corriente. | do not wear motors until you remove the cables to pass current. | Don't wear the engine until you remove the cables to pass current. |
| es04-noise10db | tiny | 0.333 | no prendas en la moto hasta que quiten los cables para pasar corriente. | Don't wear on the bike until they remove the cables to pass the current. | Don't wear the engine until you remove the cables to pass current. |
| es12-clean | tiny | 0.545 | llevo a los Bcerros al Potrero del Norte y Reviso del Vevero. | I'm taking the Bcerros to the North Potrero and Vevero's Check. | Take the calves to the north pasture and check the trough. |
| es01-clean | base | 0.071 | te puede llevar después del trabajo, pero hoy no me puedo quedar a cenar. | I can take you after work, but I can't stay for dinner today. | I can take you after work, but I can't stay for dinner today. |
| es04-clean | base | 0.083 | No prendas el motor hasta que chiten los cables para pasar corriente. | Don't wear the engine until the cables are pulled out to pass the current. | Don't wear the engine until you remove the cables to pass current. |
| es04-noise10db | base | 0.083 | No prendas el motor hasta que quiten los cables para pasar corriente. | Don't start the engine until you remove the cables to pass the current. | Don't wear the engine until you remove the cables to pass current. |
| es12-clean | base | 0.364 | Llevo a los decerros al potrero del norte y revisar el bebedero. | I take the lockups to the north pasture and check the trough. | Take the calves to the north pasture and check the trough. |

English conversation changes “can” to “could” in both ASR models. Spanish conversation changes “puedo” to “puede”, affecting person. Calves/becerros are misheard in ranch fixtures, particularly Spanish Tiny; translation then faithfully propagates the wrong ASR input. Jumper-cable/formwork errors also occur on perfect text input, so they cannot all be attributed to ASR.

For neural ES04, Tiny WER rises from 0.083 clean to 0.333 with 10 dB noise; Base remains 0.083. For neural EN04, Tiny rises from 0 to 0.182; Base remains 0. This is only two deterministic Gaussian-noise comparisons, not real farm/garage noise, accents, overlapping speakers, or microphone robustness. Formant Spanish is much less recognizable: mean WER 0.763 Tiny / 0.532 Base. Preserve these failures; neither synthetic set estimates natural conversation accuracy.

No human/fluent listening review, BLEU/COMET score, contextual LLM, M2M100, Whisper Small, streaming, microphone capture, or physical-phone test was run. Larger-model quality and production decoding improvements are unmeasured.
