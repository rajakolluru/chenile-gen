# bash completion for jgen                                   -*- shell-script -*-
#
# Install:  source <(jgen --completion bash)      (add to ~/.bashrc)
# Homebrew and the zip install this file for you. Works with bash 3.2 (macOS) and later; zsh users should use
# `jgen --completion zsh` instead.
#
# Blueprint names come from `jgen --list-blueprints`, cached for 10 minutes (or until a jar is added to
# ~/.jgen/blueprints) so that pressing Tab does not start a JVM every time.

_jgen_blueprint_names() {
    local cache="${XDG_CACHE_HOME:-$HOME/.cache}/jgen/blueprint-names"
    local stale=
    if [ ! -s "$cache" ] || [ -n "$(find "$cache" -mmin +10 2>/dev/null)" ]; then
        stale=1
    elif [ -d "$HOME/.jgen/blueprints" ] && [ -n "$(find "$HOME/.jgen/blueprints" -newer "$cache" 2>/dev/null | head -1)" ]; then
        stale=1
    fi
    if [ -n "$stale" ]; then
        mkdir -p "${cache%/*}" 2>/dev/null
        if command jgen --list-blueprints >"$cache.$$" 2>/dev/null; then
            mv -f "$cache.$$" "$cache"
        else
            rm -f "$cache.$$"
        fi
    fi
    [ -s "$cache" ] && cut -f1 "$cache"
    echo "github:"
}

_jgen() {
    local cur prev words cword
    if declare -F _get_comp_words_by_ref >/dev/null 2>&1; then
        # bash-completion is loaded: keep "github:owner/repo" as one word despite the colon.
        _get_comp_words_by_ref -n : cur prev words cword
    else
        cur="${COMP_WORDS[COMP_CWORD]}"
        prev="${COMP_WORDS[COMP_CWORD-1]}"
        # Without bash-completion, a colon splits the word: rebuild "github:..." from its pieces.
        if [ "$cur" = ":" ] || [ "$prev" = ":" ]; then
            local i=$COMP_CWORD
            [ "$cur" = ":" ] && cur="" || i=$((i - 1))
            cur="${COMP_WORDS[i-1]}:$cur"
            prev="${COMP_WORDS[i-2]}"
        fi
    fi

    local options="-c --config -e --emit-config -f --input-file -o --output-file
        -g --generate-blueprint-file -r --regenerate --into --blueprint --fetch
        --trust --offline --completion -h --help -V --version"

    case "$prev" in
        -g|--generate-blueprint-file|--blueprint|--fetch)
            COMPREPLY=( $(compgen -W "$(_jgen_blueprint_names)" -- "$cur") )
            if [ ${#COMPREPLY[@]} -eq 1 ] && [ "${COMPREPLY[0]}" = "github:" ]; then
                type compopt >/dev/null 2>&1 && compopt -o nospace 2>/dev/null
            fi
            if declare -F __ltrim_colon_completions >/dev/null 2>&1; then
                __ltrim_colon_completions "$cur"
            else
                # Bash completes only the part after the last colon: strip what is already typed up to it.
                case "$cur" in
                    *:*) local prefix="${cur%"${cur##*:}"}" kept=() c
                         for c in "${COMPREPLY[@]}"; do
                             c="${c#"$prefix"}"
                             [ -n "$c" ] && kept+=("$c")
                         done
                         COMPREPLY=( "${kept[@]}" ) ;;
                esac
            fi
            return 0 ;;
        -f|--input-file|-c|--config)
            type compopt >/dev/null 2>&1 && compopt -o filenames 2>/dev/null
            COMPREPLY=( $(compgen -f -X '!*.json' -- "$cur") $(compgen -d -- "$cur") )
            return 0 ;;
        -o|--output-file|-e|--emit-config)
            type compopt >/dev/null 2>&1 && compopt -o filenames 2>/dev/null
            COMPREPLY=( $(compgen -f -- "$cur") )
            return 0 ;;
        -r|--regenerate|--into)
            type compopt >/dev/null 2>&1 && compopt -o filenames 2>/dev/null
            COMPREPLY=( $(compgen -d -- "$cur") )
            return 0 ;;
        --completion)
            COMPREPLY=( $(compgen -W "bash zsh fish" -- "$cur") )
            return 0 ;;
    esac

    COMPREPLY=( $(compgen -W "$options" -- "$cur") )
    return 0
}

complete -o default -F _jgen jgen jgen.sh
