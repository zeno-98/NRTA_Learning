# Two-Door Interlock

## Source

The 2doors.xml demonstration model distributed with UPPAAL.

## Adaptation

The two timed doors and two interface/user components are retained with the
original constants 4, 5, 6, and 8. Formerly internal door steps use private
phase actions. Urgent closed-door handshakes are represented by zero-time
interface transitions, which preserve the relevant timed synchronization
without changing an active door clock.
