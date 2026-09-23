// STARTUP script for the isolated fixture only. These two registrations are the
// unchanged event.create declarations from the actual pack's startup_scripts/item.js.
StartupEvents.registry('item', event => {
    event.create('time_dilation_containment_unit');
    event.create('extremely_durable_plasma_cell');
});
