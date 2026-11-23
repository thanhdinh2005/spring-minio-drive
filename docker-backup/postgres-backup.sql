--
-- PostgreSQL database cluster dump
--

\restrict RPmNBcKYd1Ej9eDA5Ar8tpsC3qfdBoqSpuDZPwfA1btT8S0iRL6j1n64icjzfdv

SET default_transaction_read_only = off;

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

--
-- Roles
--

CREATE ROLE minidrive;
ALTER ROLE minidrive WITH SUPERUSER INHERIT CREATEROLE CREATEDB LOGIN REPLICATION BYPASSRLS PASSWORD 'SCRAM-SHA-256$4096:85puHt49vsKvrSiDr96Drg==$JG40kNlfxCRWht1aurFUgikxNCrd+4hCvFK3430xWIM=:8tyos33YuVKLzbbnAnGEb+8tFAfBuMlb9HoYqPKYkhs=';

--
-- User Configurations
--








\unrestrict RPmNBcKYd1Ej9eDA5Ar8tpsC3qfdBoqSpuDZPwfA1btT8S0iRL6j1n64icjzfdv

--
-- Databases
--

--
-- Database "template1" dump
--

\connect template1

--
-- PostgreSQL database dump
--

\restrict I5ArWcQNiwLfb1WcY96IeF811ypTtOKoE0cqO53lOaeFdbl7YKx72Bmnt4WWXB2

-- Dumped from database version 16.16
-- Dumped by pg_dump version 16.16

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- PostgreSQL database dump complete
--

\unrestrict I5ArWcQNiwLfb1WcY96IeF811ypTtOKoE0cqO53lOaeFdbl7YKx72Bmnt4WWXB2

--
-- Database "app_db" dump
--

--
-- PostgreSQL database dump
--

\restrict Xn0f6IThR6tuae40ose9CtrbgXbtCYubze3hrFEo3jBVpJuyv3ajMedx7UtSWl9

-- Dumped from database version 16.16
-- Dumped by pg_dump version 16.16

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: app_db; Type: DATABASE; Schema: -; Owner: minidrive
--

CREATE DATABASE app_db WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE app_db OWNER TO minidrive;

\unrestrict Xn0f6IThR6tuae40ose9CtrbgXbtCYubze3hrFEo3jBVpJuyv3ajMedx7UtSWl9
\connect app_db
\restrict Xn0f6IThR6tuae40ose9CtrbgXbtCYubze3hrFEo3jBVpJuyv3ajMedx7UtSWl9

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: documents; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.documents (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    folder_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    object_key character varying(512) NOT NULL,
    size bigint NOT NULL,
    mime_type character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.documents OWNER TO minidrive;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO minidrive;

--
-- Name: folders; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.folders (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    owner_id uuid NOT NULL,
    parent_id uuid,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.folders OWNER TO minidrive;

--
-- Name: payments; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.payments (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    txn_ref character varying(100) NOT NULL,
    amount bigint NOT NULL,
    order_info character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    transaction_no character varying(100),
    bank_code character varying(50),
    response_code character varying(10),
    transaction_status character varying(10),
    paid_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_payments_amount CHECK ((amount > 0)),
    CONSTRAINT chk_payments_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.payments OWNER TO minidrive;

--
-- Name: permissions; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.permissions (
    id uuid NOT NULL,
    folder_id uuid NOT NULL,
    shared_with_user_id uuid,
    permission_type character varying(20) NOT NULL,
    public_token character varying(64),
    granted_by_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_permission_share_target CHECK ((((shared_with_user_id IS NOT NULL) AND (public_token IS NULL)) OR ((shared_with_user_id IS NULL) AND (public_token IS NOT NULL))))
);


ALTER TABLE public.permissions OWNER TO minidrive;

--
-- Name: users; Type: TABLE; Schema: public; Owner: minidrive
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(255) NOT NULL,
    role character varying(20) DEFAULT 'USER'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    plan character varying(20) DEFAULT 'FREE'::character varying NOT NULL,
    storage_used bigint DEFAULT 0 NOT NULL,
    storage_limit bigint DEFAULT 104857600 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    premium_expires_at timestamp without time zone
);


ALTER TABLE public.users OWNER TO minidrive;

--
-- Data for Name: documents; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.documents (id, name, folder_id, owner_id, object_key, size, mime_type, created_at, updated_at) FROM stdin;
c3000000-0000-4000-8000-000000000001	project-plan.pdf	b2000000-0000-4000-8000-000000000001	a1000000-0000-4000-8000-000000000001	users/a1000000-0000-4000-8000-000000000001/project-plan.pdf	2097152	application/pdf	2026-10-17 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000002	annual-report.pdf	b2000000-0000-4000-8000-000000000002	a1000000-0000-4000-8000-000000000001	users/a1000000-0000-4000-8000-000000000001/annual-report.pdf	5242880	application/pdf	2026-10-19 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000003	meeting-notes.docx	b2000000-0000-4000-8000-000000000002	a1000000-0000-4000-8000-000000000001	users/a1000000-0000-4000-8000-000000000001/meeting-notes.docx	1048576	application/vnd.openxmlformats-officedocument.wordprocessingml.document	2026-10-24 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000004	lecture-notes.pdf	b2000000-0000-4000-8000-000000000004	a1000000-0000-4000-8000-000000000002	users/a1000000-0000-4000-8000-000000000002/lecture-notes.pdf	3145728	application/pdf	2026-10-22 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000005	assignment-01.docx	b2000000-0000-4000-8000-000000000005	a1000000-0000-4000-8000-000000000002	users/a1000000-0000-4000-8000-000000000002/assignment-01.docx	524288	application/vnd.openxmlformats-officedocument.wordprocessingml.document	2026-10-29 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000006	architecture.png	b2000000-0000-4000-8000-000000000006	a1000000-0000-4000-8000-000000000003	users/a1000000-0000-4000-8000-000000000003/architecture.png	2097152	image/png	2026-10-28 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000007	README.md	b2000000-0000-4000-8000-000000000007	a1000000-0000-4000-8000-000000000003	users/a1000000-0000-4000-8000-000000000003/README.md	16384	text/markdown	2026-11-01 10:44:26.55051	2026-11-13 10:44:26.55051
c3000000-0000-4000-8000-000000000008	resume.pdf	b2000000-0000-4000-8000-000000000009	a1000000-0000-4000-8000-000000000005	users/a1000000-0000-4000-8000-000000000005/resume.pdf	1048576	application/pdf	2026-11-10 10:44:26.55051	2026-11-13 10:44:26.55051
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	create users table	SQL	V1__create_users_table.sql	1932751630	minidrive	2026-11-13 10:44:26.304406	39	t
2	2	create folders table	SQL	V2__create_folders_table.sql	-131710922	minidrive	2026-11-13 10:44:26.389925	28	t
3	3	create documents table	SQL	V3__create_documents_table.sql	-1384660512	minidrive	2026-11-13 10:44:26.437089	28	t
4	4	create permissions table	SQL	V4__create_permissions_table.sql	-829016180	minidrive	2026-11-13 10:44:26.48042	39	t
5	5	seed data	SQL	V5__seed_data.sql	-1280410423	minidrive	2026-11-13 10:44:26.534932	19	t
6	6	create payments table	SQL	V6__create_payments_table.sql	-1513682504	minidrive	2026-11-14 03:35:15.575932	60	t
\.


--
-- Data for Name: folders; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.folders (id, name, owner_id, parent_id, created_at, updated_at) FROM stdin;
b2000000-0000-4000-8000-000000000001	Work	a1000000-0000-4000-8000-000000000001	\N	2026-10-15 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000002	Reports	a1000000-0000-4000-8000-000000000001	b2000000-0000-4000-8000-000000000001	2026-10-16 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000003	Personal	a1000000-0000-4000-8000-000000000001	\N	2026-10-24 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000004	University	a1000000-0000-4000-8000-000000000002	\N	2026-10-20 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000005	Assignments	a1000000-0000-4000-8000-000000000002	b2000000-0000-4000-8000-000000000004	2026-10-21 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000006	Projects	a1000000-0000-4000-8000-000000000003	\N	2026-10-26 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000007	Source Code	a1000000-0000-4000-8000-000000000003	b2000000-0000-4000-8000-000000000006	2026-10-29 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000008	Photos	a1000000-0000-4000-8000-000000000004	\N	2026-11-05 10:44:26.55051	2026-11-13 10:44:26.55051
b2000000-0000-4000-8000-000000000009	Documents	a1000000-0000-4000-8000-000000000005	\N	2026-11-09 10:44:26.55051	2026-11-13 10:44:26.55051
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.payments (id, user_id, txn_ref, amount, order_info, status, transaction_no, bank_code, response_code, transaction_status, paid_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.permissions (id, folder_id, shared_with_user_id, permission_type, public_token, granted_by_id, created_at, updated_at) FROM stdin;
d4000000-0000-4000-8000-000000000001	b2000000-0000-4000-8000-000000000001	a1000000-0000-4000-8000-000000000002	VIEW	\N	a1000000-0000-4000-8000-000000000001	2026-11-03 10:44:26.55051	2026-11-13 10:44:26.55051
d4000000-0000-4000-8000-000000000002	b2000000-0000-4000-8000-000000000002	a1000000-0000-4000-8000-000000000003	EDIT	\N	a1000000-0000-4000-8000-000000000001	2026-11-05 10:44:26.55051	2026-11-13 10:44:26.55051
d4000000-0000-4000-8000-000000000003	b2000000-0000-4000-8000-000000000005	\N	VIEW	demo-public-assignment-001	a1000000-0000-4000-8000-000000000002	2026-11-08 10:44:26.55051	2026-11-13 10:44:26.55051
d4000000-0000-4000-8000-000000000004	b2000000-0000-4000-8000-000000000006	a1000000-0000-4000-8000-000000000005	VIEW	\N	a1000000-0000-4000-8000-000000000003	2026-11-11 10:44:26.55051	2026-11-13 10:44:26.55051
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: minidrive
--

COPY public.users (id, email, password_hash, display_name, role, status, plan, storage_used, storage_limit, created_at, updated_at, premium_expires_at) FROM stdin;
a1000000-0000-4000-8000-000000000001	nguyenvana@gmail.com	$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2	Nguyß╗àn V─ân A	ADMIN	ACTIVE	PREMIUM	419430400	10737418240	2026-10-14 10:44:26.55051	2026-11-13 10:44:26.55051	\N
a1000000-0000-4000-8000-000000000002	tranthib@gmail.com	$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2	Trß║ºn Thß╗ï B	USER	ACTIVE	FREE	52428800	104857600	2026-10-19 10:44:26.55051	2026-11-13 10:44:26.55051	\N
a1000000-0000-4000-8000-000000000003	levanc@gmail.com	$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2	L├¬ V─ân C	USER	ACTIVE	PREMIUM	209715200	10737418240	2026-10-24 10:44:26.55051	2026-11-13 10:44:26.55051	\N
a1000000-0000-4000-8000-000000000004	phamthid@gmail.com	$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2	Phß║ím Thß╗ï D	USER	ACTIVE	FREE	0	104857600	2026-11-03 10:44:26.55051	2026-11-13 10:44:26.55051	\N
a1000000-0000-4000-8000-000000000005	hoangvane@gmail.com	$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2	Ho├áng V─ân E	USER	ACTIVE	FREE	10485760	104857600	2026-11-08 10:44:26.55051	2026-11-13 10:44:26.55051	\N
40d894b4-b1f0-4823-ac9e-90dd0c730911	huy913x@gmail.com	$2a$10$kceJbl/HRGo7Qax3HG4WeeDENqGvXIpfSFFW93Hdnkk1t1/I0TLqu	Test User	USER	ACTIVE	FREE	0	104857600	2026-11-13 10:56:20.531536	2026-11-13 10:56:20.531536	\N
\.


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: folders folders_pkey; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.folders
    ADD CONSTRAINT folders_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: payments payments_txn_ref_key; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_txn_ref_key UNIQUE (txn_ref);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_public_token_key; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_public_token_key UNIQUE (public_token);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_documents_folder_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_documents_folder_id ON public.documents USING btree (folder_id);


--
-- Name: idx_documents_owner_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_documents_owner_id ON public.documents USING btree (owner_id);


--
-- Name: idx_folders_owner_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_folders_owner_id ON public.folders USING btree (owner_id);


--
-- Name: idx_folders_parent_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_folders_parent_id ON public.folders USING btree (parent_id);


--
-- Name: idx_payments_status; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_payments_status ON public.payments USING btree (status);


--
-- Name: idx_payments_user_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_payments_user_id ON public.payments USING btree (user_id);


--
-- Name: idx_permissions_folder_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_permissions_folder_id ON public.permissions USING btree (folder_id);


--
-- Name: idx_permissions_public_token; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_permissions_public_token ON public.permissions USING btree (public_token);


--
-- Name: idx_permissions_shared_with_user_id; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_permissions_shared_with_user_id ON public.permissions USING btree (shared_with_user_id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: minidrive
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: documents documents_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES public.folders(id);


--
-- Name: documents documents_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- Name: payments fk_payments_user; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: folders folders_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.folders
    ADD CONSTRAINT folders_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- Name: folders folders_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.folders
    ADD CONSTRAINT folders_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.folders(id);


--
-- Name: permissions permissions_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES public.folders(id);


--
-- Name: permissions permissions_granted_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_granted_by_id_fkey FOREIGN KEY (granted_by_id) REFERENCES public.users(id);


--
-- Name: permissions permissions_shared_with_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: minidrive
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_shared_with_user_id_fkey FOREIGN KEY (shared_with_user_id) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict Xn0f6IThR6tuae40ose9CtrbgXbtCYubze3hrFEo3jBVpJuyv3ajMedx7UtSWl9

--
-- Database "postgres" dump
--

\connect postgres

--
-- PostgreSQL database dump
--

\restrict Mb1IZNyggyoZOOejiq7S7tsut2xztw8Y0Kl6bYInao2vkDi7ebaMcGudO0q1LRf

-- Dumped from database version 16.16
-- Dumped by pg_dump version 16.16

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- PostgreSQL database dump complete
--

\unrestrict Mb1IZNyggyoZOOejiq7S7tsut2xztw8Y0Kl6bYInao2vkDi7ebaMcGudO0q1LRf

--
-- PostgreSQL database cluster dump complete
--

