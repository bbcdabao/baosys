import mitt from 'mitt'

type Events = {
  'go-login': void
}

const emitter = mitt<Events>()

export default emitter
