extends Area2D

func _ready():
	connect('body_entered', self, '_on_body_entered')
	
func _on_body_entered(body):
	if body.is_in_group('players'):
		body.set_physics_process(false)
		body.set_process(false)
		body.get_node('AnimationPlayer').play('fall')
		yield(body.get_node('AnimationPlayer'), 'animation_finished')
		body._die(true)
		while body.dead:
			yield(get_tree().create_timer(0.02),'timeout')
		body.set_process(true)