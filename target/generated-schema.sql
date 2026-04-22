create table posts (published boolean not null, author_id bigint not null, created_at timestamp(6) with time zone, id bigserial not null, updated_at timestamp(6) with time zone, content TEXT not null, title varchar(255) not null, primary key (id));
create table refresh_tokens (revoked boolean not null, created_at timestamp(6) with time zone, expires_at timestamp(6) with time zone not null, id bigserial not null, user_id bigint not null unique, token varchar(255) not null unique, primary key (id));
create table user_roles (user_id bigint not null, role varchar(255) check (role in ('ROLE_USER','ROLE_ADMIN')));
create table users (created_at timestamp(6) with time zone, id bigserial not null, updated_at timestamp(6) with time zone, username varchar(50) not null unique, email varchar(255) not null unique, password varchar(255) not null, primary key (id));
alter table if exists posts add constraint FK6xvn0811tkyo3nfjk2xvqx6ns foreign key (author_id) references users;
alter table if exists refresh_tokens add constraint FK1lih5y2npsf8u5o3vhdb9y0os foreign key (user_id) references users;
alter table if exists user_roles add constraint FKhfh9dx7w3ubf1co1vdev94g3f foreign key (user_id) references users;
