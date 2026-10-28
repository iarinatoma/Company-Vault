# Company Vault 

**Proiect colectiv — ghid de instalare și pornire pentru echipă (Windows + VS Code)**

> **Dacă abia ai clonat proiectul și nu ai lucrat deloc la el, începe cu secțiunea „1. Prima configurare”.** Nu trebuie să știi deja Docker, Spring Boot sau Angular. Urmează pașii în ordine. Dacă ai un program instalat deja, verifică-i versiunea și sari peste instalarea lui.
>
> **Dacă ai configurat deja totul și aplicația a funcționat**, mergi direct la [„2. Cum pornim proiectul în fiecare zi”](#2-cum-pornim-proiectul-în-fiecare-zi).

## Ce este Company Vault?

Construim o aplicație internă pentru companii în care angajații pot păstra și partaja **parole, API keys, token-uri și note confidențiale**, cu acces pe roluri, criptare, istoric și audit. Este un proiect de facultate inspirat de manageri de secrete precum Bitwarden / 1Password Teams.

**Nu sunt încă implementate funcționalitățile Company Vault din enunț.** Avem starter-ul profesorului, pe care îl vom extinde în echipă.

## Ce a fost pregătit și verificat până acum?

Pe calculatorul pe care a fost pregătit proiectul, s-au verificat cu succes:

| Componentă | Ce folosim / ce s-a verificat |
| --- | --- |
| **Frontend** | Angular 21 + TypeScript + Tailwind CSS; interfața se deschide la `http://localhost:4200` |
| **Backend** | Java 25 + Spring Boot 4.1; serverul pornește pe `http://localhost:8080` |
| **Baza de date** | PostgreSQL 18 rulează într-un container Docker |
| **Docker Compose** | `backend/compose.yaml` configurează baza de date; Spring Boot o pornește automat când pornim backend-ul |
| **Login de test** | Funcționează, împreună cu pagina de administrare a utilizatorilor din starter |
| **Backend — verificări** | `mvnw.cmd test` și `mvnw.cmd verify` → `BUILD SUCCESS`; Swagger funcționează |
| **Frontend — verificări** | 23/23 teste trecute; `lint` și `build` au funcționat |

**Atenție:** asta înseamnă că **starter-ul este funcțional local pe un calculator**, nu că toți colegii l-au instalat deja și nici că toate cerințele proiectului sunt implementate.

### Cum lucrează componentele împreună?

```text
Browser — Angular               http://localhost:4200
       │  trimite cereri HTTP /api/...
       ▼
Backend — Spring Boot            http://localhost:8080
       │  citește/scrie date
       ▼
PostgreSQL 18 — Docker           localhost:5432
```

- **Frontend** = ce vezi și folosești în browser (pagini, formulare, butoane).
- **Backend** = logica aplicației, verificarea accesului, API-urile, criptarea.
- **Database** = stochează datele.
- Fiecare coleg rulează **propriul PostgreSQL**, pe **propriul laptop**. Nu vă conectați la baza de date de pe laptopul altui coleg.

## 1. Prima configurare — o singură dată, în ordine

Instrucțiunile sunt pentru **Windows / PowerShell**. Nu instala din nou programe pe care le ai deja și sunt la versiunea necesară.

### Pasul 1 — Verifică ce ai instalat

1. Deschide **PowerShell** (din Windows Search) sau terminalul din VS Code (**Terminal → New Terminal**).
2. Copiază comenzile de mai jos **pe rând** (Enter după fiecare):

```powershell
winget --version
git --version
java -version
javac -version
node -v
npm.cmd -v
docker --version
docker compose version
wsl --version
```

**Cum interpretezi rezultatul?**

- Dacă o comandă afișează o versiune, programul este găsit. Verifică să corespundă cerințelor: **Java/JDK 25**, **Node.js 24**, Docker cu **Docker Compose**, **WSL 2**.
- Dacă apare `is not recognized` / `command not found`, ori nu este instalat, ori nu este disponibil în `PATH`. Mergi la subpasul de instalare de mai jos.
- Dacă `npm -v` este blocat de PowerShell (`npm.ps1 ... running scripts is disabled`), folosește **`npm.cmd -v`**. Nu trebuie să schimbi setările de securitate ale Windows.
- **VS Code:** verifică dacă se deschide din Windows Search. Comanda `code --version` este opțională; VS Code poate fi instalat chiar dacă `code` nu este în `PATH`.
- **`winget`** este managerul de instalare Windows. Dacă nu îl ai, poți folosi linkurile oficiale de mai jos.

### Pasul 2 — Instalează DOAR ce îți lipsește

**Nu rula toate comenzile dacă ai deja programele instalate.** Fiecare comandă se execută separat în PowerShell; urmează pașii instalatorului, iar dacă Windows cere confirmare, confirmă doar instalarea dorită.

| Dacă îți lipsește… | Instalează cu PowerShell / `winget` | Ce este? |
| --- | --- | --- |
| Git | `winget install --id Git.Git --exact` | Pentru clonare, commit-uri, push/pull, branch-uri |
| VS Code | `winget install --id Microsoft.VisualStudioCode --exact` | Editorul în care lucrăm |
| **Java JDK 25** | `winget install --id EclipseAdoptium.Temurin.25.JDK --exact` | Limbajul și instrumentele necesare backend-ului |
| **Node.js 24 LTS** | `winget install --id OpenJS.NodeJS.LTS --exact` | Rulează instrumentele Angular; include `npm` / `npx` |
| Docker Desktop | `winget install --id Docker.DockerDesktop --exact` | Rulează PostgreSQL într-un container |

**Dacă `winget` nu funcționează**, instalează din paginile oficiale: [Git](https://git-scm.com/downloads), [VS Code](https://code.visualstudio.com/download), [Java 25 / Temurin](https://adoptium.net/temurin/releases/?version=25), [Node.js](https://nodejs.org/en/download), [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/). Pentru Node.js alege versiunea **24 LTS**.

**După instalări:** închide și redeschide terminalul (uneori trebuie repornit Windows), apoi **repetă comenzile de verificare de la pasul 1**. Dacă ai deja altă versiune Java / Node și încă apare aceea, verifică `PATH` sau cere ajutor înainte să continui.

#### Dacă Docker cere WSL 2

**WSL 2** permite Docker Desktop să ruleze containere Linux pe Windows.

1. Verifică în PowerShell:

   ```powershell
   wsl --version
   wsl --list --verbose
   ```

2. Dacă WSL lipsește, deschide **PowerShell → Run as administrator** și execută:

   ```powershell
   wsl --install
   ```

3. Repornește Windows dacă ți se cere. Dacă WSL există, dar trebuie actualizat, consultă [ghidul Microsoft](https://learn.microsoft.com/en-us/windows/wsl/install) și folosește `wsl --update` doar dacă este necesar.
4. Deschide **Docker Desktop**. Așteaptă să scrie **Engine running**. Folosește **Linux containers**.
5. Verifică în PowerShell:

   ```powershell
   docker version
   docker compose version
   docker ps
   ```

   La `docker version` trebuie să existe atât secțiunea **Client**, cât și **Server**, fără `Internal Server Error`. La `docker ps` este normal să apară doar antetul tabelului dacă nu rulează containere.

> **Nu trebuie să instalezi separat PostgreSQL, Maven sau Angular CLI.** PostgreSQL vine în imaginea Docker; backend-ul are Maven Wrapper (`mvnw.cmd`); Angular CLI vine prin dependențele din frontend.

### Pasul 3 — Descarcă proiectul de pe GitHub DACA NU AI FACUT O DEJA

1. Alege folderul din calculator în care vrei să ții proiectul.
2. Deschide PowerShell în acel folder. Execută:

   ```powershell
   git clone https://github.com/iarinatoma/Company-Vault.git
   cd Company-Vault
   ```

3. Deschide **VS Code → File → Open Folder → Company-Vault**.
4. Verifică în stânga că există `backend/`, `frontend/` și `backend/compose.yaml`.

**Ce este `git clone`?** Face o copie locală a repository-ului de pe GitHub, inclusiv istoricul Git. **Nu executa `git init` într-un proiect clonat.** Dacă ai clonat deja proiectul, nu îl clona din nou.


### Pasul 4 — Creează MANUAL `backend/.env` (foarte important)

**Acest fișier NU va veni de pe GitHub. Fiecare coleg trebuie să și-l creeze local.**

1. În VS Code, în panoul din stânga (**Explorer**), găsește folderul **`backend`**.
2. **Click dreapta pe `backend` → New File**.
3. Scrie exact **`.env`** și apasă Enter. Atenție: `.env`, nu `.env.txt` și nu `env`.
4. Deschide fișierul creat și scrie/copiază **exact aceste trei linii**:

   ```dotenv
   DB_PASSWORD=VALOAREA_PRIMITA_IN_PRIVAT
   JWT_SECRET=VALOAREA_PRIMITA_IN_PRIVAT
   ENCRYPTION_KEY=VALOAREA_PRIMITA_IN_PRIVAT
   ```

5. **Cere în privat persoanei care a pregătit setup-ul valorile de dezvoltare pentru cele 3 variabile.** Vei primi separat valorile pentru `DB_PASSWORD`, `JWT_SECRET` și `ENCRYPTION_KEY`. Înlocuiește **fiecare** `VALOAREA_PRIMITA_IN_PRIVAT` cu valoarea corespunzătoare. Nu scrie ghilimele, nu pune spații înainte/după `=` și nu păstra textul `VALOAREA_PRIMITA_IN_PRIVAT`.
6. Salvează: **Ctrl + S** (punctul de „modificări nesalvate” de pe tab trebuie să dispară).
7. Verifică structura:

   ```text
   Company-Vault/
   ├── backend/
   │   ├── .env              ← tocmai creat; rămâne local
   │   ├── compose.yaml      ← venit de pe GitHub
   │   ├── pom.xml
   │   └── src/
   ├── frontend/
   └── README.md
   ```

**De ce avem `.env`?** Pentru configurări sensibile: parola locală de PostgreSQL și cheile de JWT / criptare. `backend/compose.yaml` citește `DB_PASSWORD`; Spring Boot citește și el configurația din `.env`. **`.env` este ignorat de Git, deci nu se trimite pe GitHub.** Dacă există `.env.example`, îl poți folosi ca model, dar **tot trebuie să îți creezi propriul `.env` și să completezi valorile**.

**Doar pentru development:** nu folosiți chei de producție sau parole reale de companie; nu publicați valorile în README, GitHub, Trello sau capturi de ecran. Cheia de criptare trebuie păstrată stabilă pentru o bază care conține deja date criptate.

### Pasul 5 — Pornește Docker Desktop

1. Deschide **Docker Desktop** din Windows Search.
2. Așteaptă **Engine running**.
3. **Nu crea manual un container PostgreSQL și nu apăsa „Run” pentru imagine.** Compose îl creează automat la pasul următor.

**Dacă ai deja PostgreSQL instalat pe Windows**, acesta poate ocupa portul `5432`. Vezi secțiunea „Probleme frecvente” înainte să modifici orice.

### Pasul 6 — Pornește BACKEND-ul (Spring Boot)

1. În VS Code apasă **Terminal → New Terminal**. Verifică să fii în folderul principal `Company-Vault`.
2. Scrie, pe rând:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

3. **Lasă acest terminal deschis.** La prima rulare Maven poate descărca multe pachete — este normal și poate dura câteva minute.
4. Așteaptă să apară un mesaj de forma **`Started TemplateApplication`**.
5. Backend-ul rulează acum la **http://localhost:8080**. Pentru documentarea API-urilor: **http://localhost:8080/swagger-ui.html**.

**Ce se întâmplă automat?**

- `mvnw.cmd` = Maven Wrapper; construiește/pornește backend-ul fără Maven instalat global.
- Integrarea Spring Boot + `backend/compose.yaml` pornește PostgreSQL 18 în Docker (dacă este necesar).
- **Liquibase** creează/actualizează automat structura tabelelor din baza de date.
- Spring Boot se conectează la baza de date și pornește serverul.

Poți verifica PostgreSQL într-un **terminal nou** (nu în cel ocupat de Spring Boot):

```powershell
docker ps
```

Ar trebui să existe un container cu imaginea `postgres:18`, de exemplu `proiectcolectiv2026-db-1`, cu statusul `Up`. **Nu trebuie să execuți și `docker compose up` dacă backend-ul l-a pornit deja.**

### Pasul 7 — Instalează dependențele FRONTEND-ului (prima dată)

1. În VS Code creează **un al doilea terminal** cu butonul `+` sau **Terminal → New Terminal**. **Nu închide backend-ul.**
2. Din folderul principal rulează:

   ```powershell
   cd frontend
   npm.cmd ci
   ```

3. Așteaptă instalarea pachetelor în `frontend/node_modules`.

**Ce face `npm.cmd ci`?** Instalează dependențele pe baza versiunilor din `package-lock.json`. Este varianta reproductibilă pentru echipă. Profesorul scrie `npm install`; **nu trebuie să le executați pe amândouă** — aici folosim `npm.cmd ci`.

**`npm.cmd ci` se rulează la primul setup și când s-au schimbat dependențele**, nu zilnic. `node_modules/` nu se urcă pe GitHub.

### Pasul 8 — Instalează Chromium pentru testele frontend-ului (prima dată)

În **același terminal `frontend`**:

```powershell
npx.cmd playwright install chromium
```

**Ce face?** Descarcă un browser Chromium folosit de testele automate cu Playwright/Vitest. Nu este necesar pentru a vedea pagina de login, dar îl instalăm acum ca să putem rula testele profesorului ulterior. Nu trebuie repetat zilnic; se reface dacă o actualizare Playwright solicită o altă versiune de browser.

### Pasul 9 — Pornește FRONTEND-ul

În **același terminal `frontend`**:

```powershell
npm.cmd start
```

1. Așteaptă mesajul că Angular a pornit și a afișat adresa **http://localhost:4200/**.
2. Deschide adresa în browser.
3. Pentru a verifica starter-ul, autentifică-te cu **contul local de test al profesorului**:

   - **Email:** `admin@example.com`
   - **Parolă:** `Admin-Starter-1`

4. Dacă vezi dashboard-ul și exemplul de gestionare a utilizatorilor, frontend-ul comunică cu backend-ul și baza de date.

**Important:** acest utilizator de test este creat de Liquibase doar în contextul de dezvoltare (`dev`). Nu îl folosiți într-o aplicație publicată.

### Felicitări — setup-ul este gata!

Ține minte: cele două terminale par „ocupate” pentru că **serverele rulează**, nu pentru că s-au blocat. Pentru Git sau teste, deschide **un al treilea terminal** în VS Code.

---

## 2. Cum pornim proiectul în fiecare zi

**Nu reinstalăm Java, Node, pachetele npm sau Playwright la fiecare rulare.**

**1.** Deschide **Docker Desktop** și așteaptă **Engine running**.

**2.** Deschide **Company-Vault** în VS Code.

**3. Terminal 1 — backend** (din folderul principal):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Așteaptă `Started TemplateApplication`. PostgreSQL este pornit automat de Spring Boot + Docker Compose.

**4. Terminal 2 — frontend** (terminal NOU, din folderul principal):

```powershell
cd frontend
npm.cmd start
```

**5.** Deschide **http://localhost:4200/** în browser.

**6.** Scrie cod în fișierele din VS Code. Pentru comenzi Git sau teste, deschide un al treilea terminal:

```powershell
git status
```

### Când terminăm de lucrat

- În terminalul cu Angular: apasă **Ctrl + C**.
- În terminalul cu Spring Boot: apasă **Ctrl + C**.
- Spring Boot gestionează de regulă și oprirea containerului pe care l-a pornit prin Compose. Poți verifica oricând starea cu `docker ps`.
- **Datele PostgreSQL rămân în volumul Docker.** Nu rula comenzi precum `docker compose down -v` / `docker volume prune` dacă nu vrei să ștergi date.

### Când trebuie să rulez din nou `npm.cmd ci`?

Când clonezi proiectul **prima dată** sau când un `git pull` aduce modificări în `package.json` / `package-lock.json` care schimbă dependențele. Nu după fiecare modificare de componentă Angular.

### Schimbări în cod

- **Angular (`frontend/`)**: `npm.cmd start` urmărește fișierele și actualizează interfața în mod automat, de regulă fără restart.
- **Java (`backend/`)**: de regulă trebuie să oprești backend-ul (`Ctrl + C`) și să îl pornești din nou după modificări Java/configurație.
- **Baza de date**: structura se schimbă prin migrări Liquibase, nu prin editarea manuală a containerului.

---

## 3. Comenzile din README-urile profesorului — ce fac și când le folosim

**Nu sunt o listă de instalări obligatorii.** Unele sunt pentru pornire, altele pentru testare, verificare sau distribuire.

### Backend — rulezi comenzile din folderul `backend/`

| Comandă (Windows / PowerShell) | Explicație simplă | Când? |
| --- | --- | --- |
| `.\mvnw.cmd spring-boot:run` | Pornește serverul Spring Boot; la noi pornește și PostgreSQL prin Compose | Zilnic, când lucrezi |
| `.\mvnw.cmd test` | Rulează testele **unitare** Java | Când verifici codul |
| `.\mvnw.cmd verify` | Build + verificări, inclusiv teste de integrare (necesită Docker) | Înainte de PR / după modificări importante |
| `.\mvnw.cmd verify -Dit.test=ClassNameIT` | Rulează o anumită clasă de teste de integrare (`ClassNameIT` e un exemplu, nu nume real) | Când investighezi un test |
| `.\mvnw.cmd spring-boot:test-run` | Variantă **alternativă** de pornire cu o bază temporară Testcontainers | Doar dacă ai nevoie de această variantă |
| `.\mvnw.cmd spring-boot:build-image` | Construiește o **imagine Docker a backend-ului** prin Buildpacks, fără Dockerfile | La containerizare / deployment, **nu la primul setup** |

Pe macOS/Linux, scriptul Maven din README-ul original se scrie `./mvnw`. Pe Windows utilizăm `.\mvnw.cmd`.

**Swagger / OpenAPI**: dacă backend-ul este pornit, mergi la **http://localhost:8080/swagger-ui.html** ca să vezi endpoint-urile API (login, users etc.). Este pentru dezvoltare/test, nu o instalare suplimentară.

**Liquibase**: rulează automat migrările când pornește backend-ul; **nu trebuie să dai o comandă separată** pentru a crea tabelele.

### Frontend — rulezi comenzile din folderul `frontend/`

| Comandă (Windows / PowerShell) | Explicație simplă | Când? |
| --- | --- | --- |
| `npm.cmd ci` | Instalează pachetele din `package-lock.json` | Prima dată / dependențe schimbate |
| `npx.cmd playwright install chromium` | Descarcă browserul pentru teste | Prima dată / la cererea Playwright |
| `npm.cmd start` | Pornește Angular pe `localhost:4200` | Zilnic |
| `npm.cmd test` | Rulează testele frontend în **watch mode**: rămâne activ | Când dezvolți și vrei testare continuă |
| `npm.cmd run test:ci` | Rulează testele **o singură dată**, fără fereastră vizibilă de browser | Verificări rapide / CI |
| `npm.cmd run lint` | Verifică regulile de calitate/stil ale codului | Înainte de PR |
| `npm.cmd run build` | Construiește fișierele frontend pentru distribuire în `dist/` | Înainte de PR / deployment |

**De ce `npm.cmd`, nu `npm`?** În unele terminale PowerShell, scriptul `npm.ps1` este blocat de Execution Policy; `npm.cmd` funcționează fără schimbarea setării. Similar, folosim `npx.cmd`.

**Nu trebuie să rulezi și `npm test`, și `npm run test:ci` la primul setup.** Pentru o verificare unică este suficient `npm.cmd run test:ci`.

---

## 4. Explicații rapide pentru termenii din starter

| Termen | Pe înțelesul tuturor |
| --- | --- |
| **Java 25 / JDK** | Limbajul și kitul cu care compilăm/rulăm backend-ul |
| **Spring Boot** | Framework-ul Java folosit pentru server și API-uri |
| **Maven / `pom.xml`** | Managerul dependențelor Java; Maven Wrapper descarcă versiunea necesară |
| **Angular / TypeScript** | Framework și limbaj pentru interfața web |
| **npm / `package.json`** | Managerul de pachete frontend; `package-lock.json` fixează versiunile instalate |
| **Tailwind CSS** | Clase CSS pentru stilizarea interfeței |
| **PostgreSQL** | Baza de date relațională |
| **Docker Image** | „Șablonul” din care se creează un container (de exemplu `postgres:18`) |
| **Docker Container** | Instanța care rulează efectiv PostgreSQL pe laptop |
| **Docker Volume** | Păstrează datele chiar dacă un container este oprit/eliminat |
| **Docker Compose / `compose.yaml`** | Descrie ce containere, porturi și volume trebuie create |
| **`.env`** | Fișierul local cu configurații și chei care nu intră în Git |
| **Liquibase** | Menține structura bazei de date prin fișiere de migrare |
| **JPA** | Permite backend-ului Java să lucreze cu date din PostgreSQL |
| **Spring Security / JWT** | Autentificare și autorizare; JWT reprezintă un token de acces |
| **2FA / TOTP** | Al doilea factor de autentificare; pasul TOTP nu este încă implementat în pagina de login Angular |
| **Swagger / OpenAPI** | Documentația interactivă pentru endpoint-urile backend-ului |
| **JUnit / Testcontainers** | Teste Java; Testcontainers poate porni baze de date temporare pentru testele de integrare |
| **Vitest / Playwright / Chromium** | Teste automate pentru Angular, inclusiv în browser |
| **ESLint / lint** | Verifică problemele și convențiile codului frontend |
| **Lombok / MapStruct** | Biblioteci care reduc codul repetitiv și ajută la transformarea obiectelor Java |
| **ArchUnit** | Teste care verifică regulile de organizare/arhitectură ale backend-ului |
| **Signals / OnPush / Signal Forms** | Mecanisme Angular pentru starea interfeței, actualizări eficiente și formulare |
| **Build / CI** | Build = construirea și verificarea aplicației; CI = verificări automate la modificările echipei |

În starter-ul backend, funcționalitățile sunt grupate în zone precum `auth/`, `user/`, `shared/`, `infra/`. În frontend, codul este în `src/app/domains/`. **Nu trebuie să reorganizați aceste foldere ca să porniți proiectul.**

---

## 5. Probleme frecvente și ce încercăm mai întâi

### `npm.ps1 cannot be loaded` / scripturi PowerShell blocate

Folosește `npm.cmd` și `npx.cmd`, ca în acest README. Nu este nevoie să schimbi politica PowerShell.

### `java` sau `node` nu sunt recunoscute / versiune greșită

Instalează versiunea din pasul 2, apoi închide și redeschide terminalul. Verifică `java -version`, `javac -version` și `node -v`. Dacă ai mai multe versiuni instalate, cere ajutor pentru configurarea `PATH` / `JAVA_HOME`.

### `docker ps` → `Internal Server Error` / nu se conectează

Deschide Docker Desktop, așteaptă **Engine running**, apoi verifică `docker version` (trebuie să afișeze și **Server**). Dacă persistă, verificați actualizările Docker Desktop/WSL; **nu dați factory reset și nu ștergeți volume**.

### Portul `5432` este ocupat

Verifică în PowerShell:

```powershell
netstat -ano | findstr :5432
```

Dacă apare o linie `LISTENING`, identifică procesul după PID-ul de la final:

```powershell
Get-Process -Id NUMAR_PID
```

Dacă este un PostgreSQL instalat pe Windows **și nu este folosit de alt proiect**, îl poți opri temporar din `Win + R` → `services.msc` → serviciul PostgreSQL → **Stop**. **Nu opri procese necunoscute și nu schimba porturile la întâmplare.**

### `No Docker Compose file found in directory ... backend`

Verifică să existe **`backend/compose.yaml`** în proiectul clonat. Nu îl pune în alt director fără să actualizezi configurația Spring Boot.

### Backend-ul afișează `BUILD FAILURE`

Caută **primul** `Caused by:` sau `Application run failed` **deasupra** mesajului `BUILD FAILURE`. Ultima linie Maven este doar rezumatul. Verifică Docker, portul 5432 și fișierul `.env`.

### `Could not get HEAD Ref` / Git fără commit

Apare dacă ai creat un repo local cu `git init`, dar nu există încă un commit. **Dacă ai clonat proiectul după primul push, ai deja istoric Git și nu trebuie să faci nimic special.** Nu executa `git init` peste repo-ul clonat.

### Pagina nu se deschide / login-ul nu merge

- Backend-ul trebuie să afișeze **`Started TemplateApplication`**.
- Frontend-ul trebuie să afișeze **`Local: http://localhost:4200/`**.
- Verifică `docker ps`, `backend/.env` și eventualele erori din cele două terminale.
- `http://localhost:8080/swagger-ui.html` te ajută să verifici că backend-ul răspunde.

### `npm ci` afișează `vulnerabilities`

Instalarea poate fi reușită chiar dacă npm semnalează vulnerabilități în dependențe. **Anunță echipa și nu rula automat `npm audit fix --force`**, deoarece poate schimba versiuni și poate strica compatibilitatea. Problemele de securitate se analizează separat înainte de deployment.

---

## 6. Ce vom face în continuare în proiect (nu la prima instalare)

După ce fiecare coleg are mediul de lucru funcțional, implementăm cerințele din **`Project3-Company-Vault.md`**: vault-uri personale/de proiect, secret management, permisiuni, grupuri, criptare AES-GCM și envelope encryption, versionare, audit log, expirare, sharing și testare.

Pentru dezvoltarea în echipă vom folosi GitHub (branch-uri și Pull Requests), code review, teste și documentație. Pentru livrare va trebui pregătit și un **deployment reproductibil prin Docker Compose cu HTTPS**, conform cerinței profesorului. **Setup-ul de dezvoltare de mai sus nu reprezintă încă deployment-ul final.**

Documentația tehnică originală a profesorului rămâne disponibilă în:

- [`backend/README.md`](backend/README.md) — Spring Boot, Java, API, comenzi Maven, teste, arhitectură.
- [`frontend/README.md`](frontend/README.md) — Angular, autentificare, structură frontend, teste și build.

Acest README este ghidul simplificat pentru **prima configurare și pornirea zilnică a proiectului**.

yay andreeaaaaaaaaa
iarina yay
