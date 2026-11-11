# Finishing the Spring Boot layer (your part)

The Java core and React dashboard in this repo were built and tested together (70 Java tests,
24 front-end tests, one browser run against the live API). The **Spring Boot layer, both
Dockerfiles, docker-compose and the pom.xml files were written without being compiled or run**,
because the build environment could not reach Maven Central or run Docker. They are a
starting point. Expect to fix a few things. That is the exercise.

**Rule for your CV:** list Spring Boot and Docker only after steps 3 and 6 below work on your machine,
and only claim what you can explain in an interview.

## Step 0: tools
Java 21 (`java -version`), Maven 3.9+ (`mvn -v`), Node 22 (`node -v`), Docker Desktop (for step 6).

## Step 1: understand the core first (don't skip)
Read in this order: `model/` (records and enums), `rules/RuleBook.java` (the 12 rules),
`service/TriageService.java` (every command checks state and writes an audit event),
then the tests. You should be able to answer: *why is a hard rule different from a high score?
why does the SLA clock start when documents arrive? why must a decline have a reason?*

## Step 2: prove the core builds with Maven
```
mvn test            # expect 70 tests passing
mvn install         # puts triage-core into your local Maven repo (the Spring module needs it)
```
If a dependency version is rejected, adjust `pom.xml`.

## Step 3: run the Spring Boot API
```
cd spring-api
mvn spring-boot:run
curl localhost:8080/health      # {"status":"ok"}
curl localhost:8080/queue
```
Likely snags: compile errors from my unverified code; fix them and note what was wrong.
Read `CaseController` and `TriageApplication`: the core has no Spring annotations, and the
beans are created in `TriageApplication`. Be ready to explain that choice (testability, no framework lock-in).

## Step 4: run the dashboard against it
```
cd frontend
npm install
npm run dev        # http://localhost:5173
```
It should look the same as with the JDK server, because both expose the same routes.

## Step 5: add Spring tests (a good extension)
Write a `@SpringBootTest` + `MockMvc` test in `spring-api/src/test/java/` that submits a PEP case
and checks it routes to SENIOR_REVIEW, and that a bad body returns 400. Known difference to
handle: the JDK adapter reports *every* type error in one 400 response; Spring/Jackson reports one
generic message. Decide whether to improve that.

## Step 6: Docker
```
docker compose up --build      # then http://localhost:5173
```
Fix whatever breaks (my likely weak spots: the Maven layer caching and the nginx image).

## Step 7: put it on GitHub
Commit in small steps with real messages, add the screenshot (`docs/dashboard.png`), and keep the
README's "Limitations" section honest.

## Being honest about how it was made
This project was built with AI assistance. If asked, say so, and be ready to walk through any file
and change something live. Interviewers care far more about whether you understand it than whether
you typed it.
