# Halting Lab

Halting Lab is a small Kotlin CLI for exploring a toy machine, a tiny assembly-like DSL, and a bounded halting analyzer that can return `HALTS`, `LOOPS`, or `UNKNOWN`.

This project does not claim to solve the general halting problem. Its purpose is to make the limits of bounded analysis concrete and easy to inspect.

## What This Repository Contains

- A Kotlin parser for a minimal line-based DSL
- A deterministic interpreter for that DSL
- A bounded analyzer that reports `HALTS`, `LOOPS`, or `UNKNOWN`
- A corpus of 200 sample programs
- A small guide that can be read from the CLI

## Why It Exists

Some problems do not have a general algorithmic solution. The halting problem is the classic example.

You cannot write a universal procedure that decides, for every possible program, whether it will halt. What you can build is a controlled environment that shows:

- how far concrete execution gets you
- where bounded reasoning helps
- why `UNKNOWN` is sometimes the only honest answer

That is the role of Halting Lab.

## Features

- Tiny DSL with labels, arithmetic, jumps, output, and halt instructions
- Repeatable interpreter behavior on a deterministic machine model
- Bounded analysis with explicit uncertainty
- Searchable sample corpus
- Simple CLI with no external runtime framework

## Quick Start

Compile the project with `kotlinc`:

```bash
kotlinc src -include-runtime -d halting-lab.jar
```

Run a few commands:

```bash
java -jar halting-lab.jar help
java -jar halting-lab.jar list 10
java -jar halting-lab.jar analyze countdown_010 200
java -jar halting-lab.jar analyze delayed_050 50
java -jar halting-lab.jar analyze delayed_050 1000
java -jar halting-lab.jar guide primer
```

## Example

This program halts after counting down from `3`:

```text
set n 3
set ticks 0
loop:
jz n done
add ticks 1
sub n 1
jump loop
done:
out ticks
halt
```

The analyzer can prove that if the step budget is large enough. If the budget is too small, the tool may return `UNKNOWN` instead.

## CLI Commands

- `help`
- `list [limit]`
- `search <term>`
- `show <sample-name>`
- `run <sample-name> [max-steps]`
- `analyze <sample-name> [max-steps]`
- `stats`
- `guide [topic-key]`
- `guide-topics`

## DSL Summary

Supported instructions:

- `set <register> <value-or-register>`
- `add <register> <value-or-register>`
- `sub <register> <value-or-register>`
- `mul <register> <value-or-register>`
- `mod <register> <value-or-register>`
- `copy <register> <value-or-register>`
- `jump <label>`
- `jz <value-or-register> <label>`
- `jnz <value-or-register> <label>`
- `out <value-or-register>`
- `nop`
- `halt`

Notes:

- Registers default to zero
- Comments start with `#`
- Labels end with `:`
- Labels are resolved during parsing

## Meaning of the Verdicts

- `HALTS`: the program terminated within the configured step budget
- `LOOPS`: the analyzer observed a repeated full machine state
- `UNKNOWN`: the configured budget was exhausted without a proof of halting or looping

`UNKNOWN` is a core feature of this project, not an error case.

## Sample Corpus

The repository currently includes 200 sample programs across two families:

- `countdown`
  Programs that predictably halt after counting down
- `delayed`
  Programs that spend time in a warmup phase before halting

These two families are useful for showing how the same analyzer can quickly prove some cases and honestly refuse to overclaim in others.

## Project Layout

- `src/haltinglab/Model.kt`
- `src/haltinglab/Parser.kt`
- `src/haltinglab/Runtime.kt`
- `src/haltinglab/Corpus.kt`
- `src/haltinglab/Cli.kt`
- `src/haltinglab/Main.kt`
- `corpus/countdown.pack`
- `corpus/delayed_halt.pack`
- `guide/primer.txt`

## Example Workflow

Try this sequence:

```bash
java -jar halting-lab.jar list 8
java -jar halting-lab.jar show countdown_005
java -jar halting-lab.jar analyze countdown_005 40
java -jar halting-lab.jar analyze delayed_050 50
java -jar halting-lab.jar analyze delayed_050 1000
```

That usually demonstrates the transition from a low-budget `UNKNOWN` result to a high-budget `HALTS` result.

## Publishing to a New GitHub Repository

If you want to push this project to a new GitHub repository, a typical flow is:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
```

If you use GitHub CLI:

```bash
gh repo create halting-lab --public --source=. --remote=origin --push
```

If you create the repository on GitHub first, then push manually:

```bash
git remote add origin git@github.com:<your-account>/<your-repo>.git
git push -u origin main
```

## Future Extensions

Possible next steps:

- add a loop-heavy corpus that produces more `LOOPS`
- add trace export to JSON or CSV
- add a small REPL
- add parser diagnostics with richer error reporting
- add regression tests for selected samples

## License

No license file has been added yet. If you plan to publish this repository, add a license before making it public.
