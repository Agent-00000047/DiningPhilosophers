// Fork messages
event TryAcquire : (requester: machine, philosopherId: int);
event Release : int;
event AcquireSuccess;
event AcquireFailed;

// Spec events for monitoring
event ePhilosopherAcquiredOneFork : int;
event ePhilosopherReleasedForks : int;
event eSize : int;

type PhilosopherConfig = (id: int, leftFork: machine, rightFork: machine, is_left_first: bool);

// Fork machine
machine Fork {
    var holder: int;
    var isHeld: bool;
    var forkId: int;
    
    start state Available {
        entry {
            holder = -1;
            isHeld = false;
            forkId = 0;
        }
        
        on TryAcquire do (payload: (requester: machine, philosopherId: int)) {
            if (!isHeld) {
                // Fork is available - grant it
                holder = payload.philosopherId;
                isHeld = true;
                send payload.requester, AcquireSuccess;
                goto NotAvailable;
            } else {
                // Fork is already taken - deny request
                send payload.requester, AcquireFailed;
            }
        }
    }
    
    state NotAvailable {
        on TryAcquire do (payload: (requester: machine, philosopherId: int)) {
            // Fork is not available - always deny
            send payload.requester, AcquireFailed;
        }
        
        on Release do (philosopherId: int) {
            if (isHeld && holder == philosopherId) {
                // Valid release
                holder = -1;
                isHeld = false;
                goto Available;
            }
        }
    }
}

machine Philosopher {
    var id: int;
    var leftFork: machine;
    var rightFork: machine;
    var hasLeftFork: bool;
    var hasRightFork: bool;
    var is_left_first: bool;
    
    start state Init {
        entry (config: PhilosopherConfig) {
            id = config.id;
            leftFork = config.leftFork;
            rightFork = config.rightFork;
            hasLeftFork = false;
            hasRightFork = false;
            is_left_first = config.is_left_first;
            goto Thinking;
        }
    }
    
    state Thinking {
        entry {
            goto Hungry;
        }   
    }
    
    state Hungry {
        
        entry {
            // Try to acquire left fork first
            if (is_left_first) {
                send leftFork, TryAcquire, (requester = this, philosopherId = id);
            } else {
                send rightFork, TryAcquire, (requester = this, philosopherId = id);
            }
            
        }
        
        on AcquireSuccess do {
            hasLeftFork = true;
            announce ePhilosopherAcquiredOneFork, id;
            goto TryToGetRightFork;
        }
        
        on AcquireFailed do {
            goto Hungry;
        }
    }

    state TryToGetRightFork {
        entry {
            // Try to acquire left fork first
            if (is_left_first) {
                send rightFork, TryAcquire, (requester = this, philosopherId = id);
            } else {
                send leftFork, TryAcquire, (requester = this, philosopherId = id);
            }
        }
        
        on AcquireSuccess do {
            hasRightFork = true;
            goto Eating;
        }
        
        on AcquireFailed do { 
            goto TryToGetRightFork;
        }
    }
    
    state Eating {
        entry {
            // Release both forks and announce
            if (hasLeftFork) {
                send leftFork, Release, id;
                hasLeftFork = false;
            }
            if (hasRightFork) {
                send rightFork, Release, id;
                hasRightFork = false;
            }
            announce ePhilosopherReleasedForks, id;
            
            // Go back to thinking
            goto Thinking;
        }
    }
}

// Main machine that sets up the dining philosophers scenario
machine Main {
    var N: int;
    var Forks: seq[machine];
    var Philosophers: seq[machine];
    var i: int;

    start state Init {
        entry {
            N = 5;
            Forks = default(seq[machine]);
            Philosophers = default(seq[machine]);
            
            announce eSize, N;
            
            // Create Fork machines
            i = 0;
            while (i < N) {
                Forks += (0, new Fork());
                i = i + 1;
            }

            // Create Philosopher machines
            i = 0;
            while (i < N) {
                Philosophers += (0, new Philosopher((
                    id = i, 
                    leftFork = Forks[i], 
                    rightFork = Forks[(i + 1) % N],
                    is_left_first = true
                )));
                i = i + 1;
            }
        }
    }
}

// Main machine that sets up the dining philosophers scenario
machine Main_NODL {
    var N: int;
    var Forks: seq[machine];
    var Philosophers: seq[machine];
    var i: int;

    start state Init {
        entry {
            N = 5;
            Forks = default(seq[machine]);
            Philosophers = default(seq[machine]);
            
            announce eSize, N;
            
            // Create Fork machines
            i = 0;
            while (i < N) {
                Forks += (0, new Fork());
                i = i + 1;
            }

            // Create Philosopher machines
            i = 0;
            while (i < N) {
                if (i == N-1) {
                    // Last philosopher acquires right fork first to avoid deadlock
                    Philosophers += (0, new Philosopher((
                        id = i, 
                        leftFork = Forks[i], 
                        rightFork = Forks[(i + 1) % N],
                        is_left_first = false
                    )));
                } else {
                    Philosophers += (0, new Philosopher((
                        id = i, 
                        leftFork = Forks[i], 
                        rightFork = Forks[(i + 1) % N],
                        is_left_first = true
                    )));
                }
                i = i + 1;
            }
        }
    }
}


module Main = { Main };
module Main_NODL = { Main_NODL };
module Philosopher = { Philosopher };
module Fork = { Fork };

test DeadLockImpl [main=Main]: assert DeadlockDetector in (union Main, Philosopher, Fork);
test NoDeadLockImpl [main=Main_NODL]: assert DeadlockDetector in (union Main_NODL, Philosopher, Fork);
