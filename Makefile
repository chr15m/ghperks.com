all: server.js build

server.js: src/**/*.cljs shadow-cljs.edn
	npx shadow-cljs release server --debug

build: $(shell find src) public/*
	mkdir -p build
	npx shadow-cljs release app
	rsync -aLz --exclude js --exclude '.*.swp' public/ build
	touch build

node_modules: package.json
	npm i

.PHONY: watch watcher server repl

server:
	rm -f devserver.js
	until [ -f devserver.js ]; do sleep 1; done
	sleep 1 && while [ 1 ]; do node devserver.js; sleep 3; done

watcher:
	npx shadow-cljs watch server app

watch:
	make -j2 watcher server

repl: node_modules
	npx shadow-cljs cljs-repl app

clean:
	rm -rf build

