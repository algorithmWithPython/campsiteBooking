CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table booking(
    id serial primary key,
    booking_id uuid default uuid_generate_v4(),
    name text not null,
    email text not null,
    constraint booking_booking_id unique (booking_id)
);

create table booking_date(
    id serial primary key,
    booking_id integer not null,
    booked_date date not null,
    constraint booking_date_booked_date unique (booked_date),
    constraint fk_booking foreign key(booking_id)
        references booking(id)
        on delete cascade
);
