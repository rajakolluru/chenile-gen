# fish completion for jgen
#
# Install:  jgen --completion fish > ~/.config/fish/completions/jgen.fish
# Homebrew installs it for you.
#
# Blueprint names come from `jgen --list-blueprints`, cached for 10 minutes (or until a jar is added to
# ~/.jgen/blueprints) so that pressing Tab does not start a JVM every time.

function __jgen_blueprints
    set -l dir $HOME/.cache
    set -q XDG_CACHE_HOME; and set dir $XDG_CACHE_HOME
    set -l cache $dir/jgen/blueprint-names
    set -l stale 0
    if not test -s $cache
        set stale 1
    else
        set -l expired (find $cache -mmin +10 2>/dev/null)
        set -l added
        test -d $HOME/.jgen/blueprints; and set added (find $HOME/.jgen/blueprints -newer $cache 2>/dev/null | head -1)
        if test -n "$expired"; or test -n "$added"
            set stale 1
        end
    end
    if test $stale -eq 1
        mkdir -p $dir/jgen 2>/dev/null
        if command jgen --list-blueprints >$cache.$fish_pid 2>/dev/null
            mv -f $cache.$fish_pid $cache
        else
            rm -f $cache.$fish_pid
        end
    end
    test -s $cache; and cat $cache
    printf '%s\t%s\n' 'github:' 'a GitHub reference: github:owner/repo[@version]'
end

complete -c jgen -f
complete -c jgen -s c -l config -r -F -d 'Use this JGen configuration'
complete -c jgen -s e -l emit-config -r -F -d 'Emit the configuration; with -g, a folder for samples'
complete -c jgen -s f -l input-file -r -F -d 'Generate from a JSON input file'
complete -c jgen -s o -l output-file -r -F -d 'Write the sample input to this file'
complete -c jgen -s g -l generate-blueprint-file -x -a '(__jgen_blueprints)' -d 'Emit a sample input for a blueprint'
complete -c jgen -s r -l regenerate -x -a '(__fish_complete_directories)' -d 'Regenerate a generated project'
complete -c jgen -l into -x -a '(__fish_complete_directories)' -d 'Folder to regenerate into'
complete -c jgen -l blueprint -x -a '(__jgen_blueprints)' -d 'Regenerate with this blueprint instead'
complete -c jgen -l fetch -x -a '(__jgen_blueprints)' -d 'Fetch, verify and cache a blueprint'
complete -c jgen -l trust -d 'Trust the blueprint sources used in this run'
complete -c jgen -l offline -d 'Use only installed and cached blueprints'
complete -c jgen -l completion -x -a 'bash zsh fish' -d 'Print a completion script'
complete -c jgen -s h -l help -d 'Show help'
complete -c jgen -s V -l version -d 'Show the version'
