# Bridge and Torch

## Source

The bridge.xml demonstration model distributed with UPPAAL.

## Adaptation

The packaged benchmark is the three-participant instance with crossing times
5, 10, and 20 minutes represented uniformly as 1, 2, and 4 five-minute units.
Shared control information, including the torch side and holder count, is
represented by the explicit torch component. Participant identity is carried
by the synchronized participant locations, and indexed take and release actions
make each handshake explicit.
